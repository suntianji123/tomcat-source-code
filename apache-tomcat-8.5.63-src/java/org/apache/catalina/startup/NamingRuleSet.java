/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.startup;


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * 命名规则集合类
 */
@SuppressWarnings("deprecation")
public class NamingRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 父标签
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    /**
     * Construct an instance of this <code>RuleSet</code> with the default
     * matching pattern prefix.
     */
    public NamingRuleSet() {
        this("");
    }


    /**
     * 实例化一个命名规则集合类
     * @param prefix 命名前缀
     */
    public NamingRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加规则的来源对象
     * @param digester 规则来源对象
     */
    @Override
    public void addRuleInstances(Digester digester) {

        //创建ContextEjb对象
        digester.addObjectCreate(prefix + "Ejb",
                                 "org.apache.tomcat.util.descriptor.web.ContextEjb");
        //设置ContextEjb对象所有的属性值（有排除的属性值时 排除属性值）
        digester.addRule(prefix + "Ejb", new SetAllPropertiesRule());
        //将ContextEjb对象作为参数，执行父标签对象的addEjb方法
        digester.addRule(prefix + "Ejb",
                new SetNextNamingRule("addEjb",
                            "org.apache.tomcat.util.descriptor.web.ContextEjb"));

        //创建ContextEnvironment对象
        digester.addObjectCreate(prefix + "Environment",
                                 "org.apache.tomcat.util.descriptor.web.ContextEnvironment");
        //设置ContextEnvironment对象的属性值
        digester.addSetProperties(prefix + "Environment");

        //将ContextEnvironment对象作为参数 执行父对象的addEnvironment方法
        digester.addRule(prefix + "Environment",
                            new SetNextNamingRule("addEnvironment",
                            "org.apache.tomcat.util.descriptor.web.ContextEnvironment"));

        //创建ContextLocalEjb对象
        digester.addObjectCreate(prefix + "LocalEjb",
                                 "org.apache.tomcat.util.descriptor.web.ContextLocalEjb");
        //设置ContextLocalEjb对象所有的属性值（排除不需要设置的属性值）
        digester.addRule(prefix + "LocalEjb", new SetAllPropertiesRule());

        //将ContextLocalEjb作为参数 执行父标签对象的addLocalEjb方法
        digester.addRule(prefix + "LocalEjb",
                new SetNextNamingRule("addLocalEjb",
                            "org.apache.tomcat.util.descriptor.web.ContextLocalEjb"));

        //创建ContextResource对象
        digester.addObjectCreate(prefix + "Resource",
                                 "org.apache.tomcat.util.descriptor.web.ContextResource");
        //设置ContextResource对象所有的属性值（排除不需要设置的属性值）
        digester.addRule(prefix + "Resource", new SetAllPropertiesRule());
        //将ContextResource对象作为参数 执行父标签对象的addResource
        digester.addRule(prefix + "Resource",
                new SetNextNamingRule("addResource",
                            "org.apache.tomcat.util.descriptor.web.ContextResource"));

        //创建ContextResourceEnvRef对象
        digester.addObjectCreate(prefix + "ResourceEnvRef",
            "org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef");
        //设置ContextResourceEnvRef所有属性（排除不需要设置的属性）
        digester.addRule(prefix + "ResourceEnvRef", new SetAllPropertiesRule());
        //将ContextResourceEnvRef作为参数 执行父标签对象的addResourceEnvRef方法
        digester.addRule(prefix + "ResourceEnvRef",
                new SetNextNamingRule("addResourceEnvRef",
                            "org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef"));

        //创建ContextService方法
        digester.addObjectCreate(prefix + "ServiceRef",
            "org.apache.tomcat.util.descriptor.web.ContextService");
        //设置ContextService对象所有的属性值（排除不需要设置的属性）
        digester.addRule(prefix + "ServiceRef", new SetAllPropertiesRule());
        //将ContextService对象作为参数 执行父标签对象的addService方法
        digester.addRule(prefix + "ServiceRef",
                new SetNextNamingRule("addService",
                            "org.apache.tomcat.util.descriptor.web.ContextService"));

        //创建ContextTransaction对象
        digester.addObjectCreate(prefix + "Transaction",
            "org.apache.tomcat.util.descriptor.web.ContextTransaction");
        //设置ContextTransaction对象所有的属性
        digester.addRule(prefix + "Transaction", new SetAllPropertiesRule());
        //将ContextTransaction对象作为参数 执行父标签对象的setTransaction方法
        digester.addRule(prefix + "Transaction",
                new SetNextNamingRule("setTransaction",
                            "org.apache.tomcat.util.descriptor.web.ContextTransaction"));

    }


}
