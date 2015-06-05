package com.amlinv.javasched.cucumber;

import cucumber.api.junit.Cucumber;
import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;

/**
 * Created by art on 12/9/14.
 */
@RunWith(Cucumber.class)
@CucumberOptions(plugin = "json:target/cucumber-report.json")
public class TestCucumberRunner {
}
