package com.redhat.ceylon.common.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.redhat.ceylon.common.Constants;
import com.redhat.ceylon.common.FileUtil;
import com.redhat.ceylon.common.OSUtil;

public abstract class ServiceToolLoader extends ToolLoader {

    private final Class<?> serviceClass;
    private Set<String> pathPlugins;
    private List<String> toolClassNames;
    
    public ServiceToolLoader(Class<?> serviceClass) {
        super();
        this.serviceClass = serviceClass;
    }
    
    public ServiceToolLoader(ClassLoader loader, Class<?> serviceClass) {
        super(loader);
        this.serviceClass = serviceClass;
    }

    protected Enumeration<URL> getServiceMeta() {
        /* Use the same conventions as java.util.ServiceLoader but without 
         * requiring us to load the Service classes
         */
        Enumeration<URL> resources;
        try {
            resources = loader.getResources("META-INF/services/"+serviceClass.getName());
        } catch (IOException e) {
            throw new ToolException(e);
        }
        return resources;
    }
    
    private List<String> parseServiceInfo(final URL url) {
        List<String> result = new ArrayList<>();
        try {
            URLConnection con = url.openConnection();
            con.setUseCaches(false);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            try {
                String className = reader.readLine();
                while (className != null) {
                    className = className.trim().replaceAll("#.*", "");
                    if (!className.isEmpty()) {
                        result.add(className);
                    }
                    className = reader.readLine();
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new ToolException("Error reading service file " + url, e);
        }
        return result;
    }

    @Override
    protected synchronized Iterable<String> toolClassNames() {
        if (toolClassNames == null) {
            List<String> result = new ArrayList<>();
            Enumeration<URL> urls = getServiceMeta();
            while (urls.hasMoreElements()) {
                result.addAll(parseServiceInfo(urls.nextElement()));
            }
            result.addAll(getPathPlugins());
            toolClassNames = Collections.unmodifiableList(result);
        }
        return toolClassNames;
    }

    protected Set<String> getPathPlugins() {
        if(pathPlugins == null){
            pathPlugins = new TreeSet<String>();
            findPathPlugins();
        }
        return pathPlugins;
    }

    private void findPathPlugins() {
        Set<String> names = new HashSet<String>();
        // First the ones from CEYLON_HOME/bin
        File ceylonHome = FileUtil.getInstallDir();
        if (ceylonHome != null) {
            findPathPlugins(new File(ceylonHome, Constants.CEYLON_BIN_DIR), names);
        }
        // Then look in /etc/ceylon/bin and /etc/ceylon/bin/{moduleName}/
        // (or their equivalents on Windows and MacOS)
        File systemDir = new File(FileUtil.getSystemConfigDir(), Constants.CEYLON_BIN_DIR);
        findPathPlugins(systemDir, names);
        // Then look in ~/.ceylon/bin and ~/.ceylon/bin/{moduleName}/
        File defUserDir = new File(FileUtil.getDefaultUserDir(), Constants.CEYLON_BIN_DIR);
        findPathPlugins(defUserDir, names);
        // And finally in the user's PATH
        File[] paths = FileUtil.getExecPath();
        for (File part : paths) {
            findPluginInPath(part, names);
        }
    }

    private void findPathPlugins(File dir, Set<String> names) {
        // Look in dir 
        findPluginInPath(dir, names);
        // And in every installed script plugin in <dir>/{moduleName}/
        if(dir.isDirectory() && dir.canRead()){
            for(File scriptPluginDir : dir.listFiles()){
                if(scriptPluginDir.isDirectory()){
                    findPluginInPath(scriptPluginDir, names);
                }
            }
        }
    }

    private void findPluginInPath(File dir, final Set<String> names) {
        if(dir.isDirectory() && dir.canRead()){
            // listing /usr/bin with >2k entries takes about 100ms using File.listFiles(Filter) and 39ms with NIO2
            // and checking for file name before file type
            DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {
                public boolean accept(Path f) throws IOException {
                    String fileName = f.getFileName().toString();
                    if(fileName.toLowerCase().startsWith("ceylon-") && Files.isRegularFile(f) && Files.isExecutable(f)){
                        String name = fileName.substring(7);
                        if(OSUtil.isWindows()){
                            // script must end with ".bat"
                            if(!name.toLowerCase().endsWith(".bat"))
                                return false;
                            // strip it
                            name = name.substring(0, name.length()-4);
                        }
                        // refuse any name with dots in there (like ceylon-completion.bash)
                        if(name.indexOf('.') != -1)
                            return false;
                        // also refuse ceylon-sh-setup
                        if(name.equalsIgnoreCase("sh-setup"))
                            return false;
                        // we're good if it's unique
                        return names.add(name);
                    }
                    return false;
                }
            };
            
            try (DirectoryStream<Path>  stream = Files.newDirectoryStream(dir.toPath(), filter)){
                for(Path sub : stream){
                    String name = SCRIPT_PREFIX+sub.toAbsolutePath().toString();
                    pathPlugins.add(name);
                }
            } catch (IOException e) {
                e.printStackTrace();
                // too bad, give up
            }
        }
    }
}
