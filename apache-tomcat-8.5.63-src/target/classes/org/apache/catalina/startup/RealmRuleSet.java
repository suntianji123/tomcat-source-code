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
 * 领域规则解析器类
 */
@SuppressWarnings("deprecation")
public class RealmRuleSet extends RuleSetBase {


    private static final int MAX_NESTED_REALM_LEVELS = Integer.getInteger(
            "org.apache.catalina.startup.RealmRuleSet.MAX_NESTED_REALM_LEVELS",
            3).intValue();

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
    public RealmRuleSet() {
        this("");
    }


    /**
     * 实例化一个领域标签解析规则对象
     * @param prefix 前缀
     */
    public RealmRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加规则
     * @param digester 规则管理对象
     */
    @Override
    public void addRuleInstances(Digester digester) {
        //获取前缀
        StringBuilder pattern = new StringBuilder(prefix);

        //添加创建Realm标签对象规则 并且设置属性 将标签对象作为参数 执行父类标签的方法 setRealm 或者 addRealm
        //Server/Service/Engine/Realm 将当前Realm对象作为参数 执行Engine的setRealm方法
        //Server/Service/Engine/Realm/Realm 将当前Realm对象作为参数 执行上一个Realm的addRealm方法
        for (int i = 0; i < MAX_NESTED_REALM_LEVELS; i++) {
            if (i > 0) {
                pattern.append('/');
            }
            pattern.append("Realm");
            addRuleInstances(digester, pattern.toString(), i == 0 ? "setRealm" : "addRealm");
        }
    }

    /**
     * 添加规则实例
     * @param digester 规则管理对象
     * @param pattern 匹配器
     * @param methodName 方法名
     */
    private void addRuleInstances(Digester digester, String pattern, String methodName) {
        //添加创建对象规则
        digester.addObjectCreate(pattern, null /* MUST be specified in the element */,
                "className");
        //添加设置属性规则
        digester.addSetProperties(pattern);
        //添加将当前标签对象作为参数 执行父标签对象方法的规则
        digester.addSetNext(pattern, methodName, "org.apache.catalina.Realm");
        digester.addRuleSet(new CredentialHandlerRuleSet(pattern + "/"));
    }
}
