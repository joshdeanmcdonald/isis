/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.core.runtime.runner.opts;

import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.isis.core.commons.config.IsisConfigurationBuilder;
import org.apache.isis.core.runtime.optionhandler.BootPrinter;
import org.apache.isis.core.runtime.optionhandler.OptionHandlerAbstract;
import org.apache.isis.core.runtime.system.SystemConstants;

public class OptionHandlerFixtureFromEnvironmentVariable extends OptionHandlerAbstract {

    private static final Logger LOG = LoggerFactory.getLogger(OptionHandlerFixtureFromEnvironmentVariable.class);

    private String fixtureClassName;

    @Override
    @SuppressWarnings("static-access")
    public void addOption(final Options options) {
        // no-op
    }

    @Override
    public boolean handle(final CommandLine commandLine, final BootPrinter bootPrinter, final Options options) {
        Map<String, String> properties = System.getenv();
        for (String key : properties.keySet()) {
            if (key.equalsIgnoreCase("IsisFixture") || key.equalsIgnoreCase("IsisFixtures")) {
                this.fixtureClassName = properties.get(key);
                return true;
            }
        }
        return true;
    }

    @Override
    public void primeConfigurationBuilder(final IsisConfigurationBuilder isisConfigurationBuilder) {
        if(fixtureClassName == null) {
            return;
        }
        prime(isisConfigurationBuilder, SystemConstants.FIXTURE_KEY, fixtureClassName);
    }

    private static void prime(IsisConfigurationBuilder isisConfigurationBuilder, String key, String value) {
        LOG.info("priming: " + key + "=" + value);
        isisConfigurationBuilder.add(key, value);
    }

}
