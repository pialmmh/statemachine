package com.telcobright.statemachine.humantests;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages({"com.telcobright.statemachine.humantests"})
@IncludeClassNamePatterns({".*Tests"})
public class HumanMasterTestRunner {
}
