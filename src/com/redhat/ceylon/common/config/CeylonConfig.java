package com.redhat.ceylon.common.config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;


public class CeylonConfig {
    private HashMap<String, String[]> options;
    private HashMap<String, HashSet<String>> sectionNames;
    private HashMap<String, HashSet<String>> optionNames;
    
    private volatile static CeylonConfig instance;
    
    public static CeylonConfig get() {
        if (instance == null) {
            synchronized (CeylonConfig.class) {
                if (instance == null) {
                    instance = createFromLocalDir(new File("."));
                }
            }
        }
        return instance;
    }
    
    public static void set(CeylonConfig config) {
        instance = config;
    }
    
    public static String get(String key) {
        return get().getOption(key);
    }
    
    public static String get(String key, String defaultValue) {
        return get().getOption(key, defaultValue);
    }
    
    public static CeylonConfig createFromLocalDir(File localDir) {
        return ConfigParser.loadDefaultConfig(localDir);
    }
    
    public CeylonConfig() {
        options = new HashMap<String, String[]>();
        sectionNames = new HashMap<String, HashSet<String>>();
        sectionNames.put("", new HashSet<String>());
        optionNames = new HashMap<String, HashSet<String>>();
    }
    
    public static class Key {
        private String subsectionName; 
        private String optionName;
        private String sectionName;
        private String parentSectionName;
        
        public String getSubsectionName() {
            return subsectionName;
        }


        public String getOptionName() {
            return optionName;
        }


        public String getSectionName() {
            return sectionName;
        }


        public String getParentSectionName() {
            return parentSectionName;
        }

        public Key(String key) {
            if (key == null) {
                throw new IllegalArgumentException("Illegal key");
            }
            String[] parts = key.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Illegal key");
            }
            subsectionName = parts[parts.length - 2]; 
            optionName = parts[parts.length - 1];
            parentSectionName = "";
            if (parts.length > 2) {
                for (int i = 0; i < parts.length - 2; i++) {
                    if (i > 0) {
                        parentSectionName += '.';
                    }
                    parentSectionName += parts[i];
                }
                sectionName = parentSectionName + '.' + subsectionName;
            } else {
                sectionName = subsectionName;
            }
        }
    }
    
    private void initLookupKey(String key) {
        Key k = new Key(key);

        if (!k.getParentSectionName().isEmpty()) {
            initLookupKey(k.getParentSectionName() + ".#");
        }
        
        HashSet<String> psn = sectionNames.get(k.getParentSectionName());
        psn.add(k.getSubsectionName());
        
        HashSet<String> sn = sectionNames.get(k.getSectionName());
        if (sn == null) {
            sn = new HashSet<String>();
            sectionNames.put(k.getSectionName(), sn);
        }
        
        if (!"#".equals(k.getOptionName())) {
            HashSet<String> on = optionNames.get(k.getSectionName());
            if (on == null) {
                on = new HashSet<String>();
                optionNames.put(k.getSectionName(), on);
            }
            on.add(k.getOptionName());
        }
    }
    
    public synchronized boolean isOptionDefined(String key) {
        return options.containsKey(key);
    }
    
    public synchronized String[] getOptionValues(String key) {
        return options.get(key);
    }
    
    public synchronized void setOptionValues(String key, String[] values) {
        if (values != null && values.length > 0) {
            options.put(key, values);
            initLookupKey(key);
        } else {
            removeOption(key);
        }
    }
    
    public String getOption(String key) {
        String[] result = getOptionValues(key);
        return (result != null) ? result[0] : null;
    }
    
    public String getOption(String key, String defaultValue) {
        String result = getOption(key);
        return (result != null) ? result : defaultValue;
    }
    
    public void setOption(String key, String value) {
        setOptionValues(key, new String[] { value });
    }
    
    public Long getNumberOption(String key) {
        String result = getOption(key);
        if (result != null) {
            try {
                return Long.valueOf(result);
            } catch (NumberFormatException e) {
                // Do nothing (logging would spam in case of a user configuration error)
            }
        }
        return null;
    }
    
    public long getNumberOption(String key, long defaultValue) {
        String result = getOption(key);
        if (result != null) {
            try {
                return Long.parseLong(result);
            } catch (NumberFormatException e) {
                // Do nothing (logging would spam in case of a user configuration error)
            }
        }
        return defaultValue;
    }
    
    public void setNumberOption(String key, long value) {
        setOption(key, Long.toString(value));
    }
    
    public Boolean getBoolOption(String key) {
        String result = getOption(key);
        if (result != null) {
            return "true".equals(result) || "on".equals(result) || "yes".equals(result) || "1".equals(result);
        }
        return null;
    }
    
    public boolean getBoolOption(String key, boolean defaultValue) {
        Boolean result = getBoolOption(key);
        if (result != null) {
            return result.booleanValue();
        }
        return defaultValue;
    }
    
    public void setBoolOption(String key, boolean value) {
        setOption(key, Boolean.toString(value));
    }

    public synchronized void removeOption(String key) {
        options.remove(key);
        
        Key k = new Key(key);
        HashSet<String> on = optionNames.get(k.getSectionName());
        if (on != null) {
            on.remove(k.getOptionName());
        }
    }
    
    public synchronized boolean isSectionDefined(String section) {
        return sectionNames.containsKey(section);
    }
    
    /**
     * Returns the list of all section names, the root section names
     * or the sub section names of the given section depending on the
     * argument being passed
     * @param section Returns the subsections of the section being passed.
     * Will return the root section names if being passed an empty string.
     * And will return all section names if being passed null.
     * @return An array of the requested section names
     */
    public synchronized String[] getSectionNames(String section) {
        HashSet<String> sn;
        if (section != null) {
            sn = sectionNames.get(section);
        } else {
            sn = new HashSet<String>(sectionNames.keySet());
            sn.remove("");
        }
        String[] res = new String[sn.size()];
        return sn.toArray(res);
    }
    
    public synchronized String[] getOptionNames(String section) {
        if (section == null) {
            String[] res = new String[options.keySet().size()];
            return options.keySet().toArray(res);
        } else {
            if (isSectionDefined(section)) {
                HashSet<String> on = optionNames.get(section);
                if (on != null) {
                    String[] res = new String[on.size()];
                    return on.toArray(res);
                } else {
                    return new String[0];
                }
            } else {
                return null;
            }
        }
    }
    
    public synchronized CeylonConfig merge(CeylonConfig local) {
        for (String key : local.getOptionNames(null)) {
            String[] values = local.getOptionValues(key);
            setOptionValues(key, values);
        }
        return this;
    }

    public CeylonConfig copy() {
        CeylonConfig cfg = new CeylonConfig();
        cfg.merge(this);
        return cfg;
    }

    @Override
    public String toString() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ConfigWriter.write(this, out);
            return out.toString("UTF-8");
        } catch (IOException e) {
            return super.toString();
        }
    }
}
