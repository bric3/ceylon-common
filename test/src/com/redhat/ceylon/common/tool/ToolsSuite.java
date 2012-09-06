package com.redhat.ceylon.common.tool;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;


@RunWith(Suite.class) 
@SuiteClasses({
    ToolLoaderTest.class,
    ToolLoaderTest.class,
    ToolFactoryTest.class,
    MultiplicityTest.class,
    TopLevelToolTest.class,
    WordWrapTest.class,
    BashCompletionToolTest.class
})
public class ToolsSuite {

    public ToolsSuite() {
        // TODO Auto-generated constructor stub
    }

}
