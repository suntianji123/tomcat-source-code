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
 * Realm标签下的子标签凭证处理器器标签解析规则类
 */
@SuppressWarnings("deprecation")
public class CredentialHandlerRuleSet extends RuleSetBase {


    private static final int MAX_NESTED_LEVELS = Integer.getInteger(
            "org.apache.catalina.startup.CredentialHandlerRuleSet.MAX_NESTED_LEVELS",
            3).intValue();

    // ----------------------------------------------------- Instance Variables


    /**
     * 标签前缀
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    /**
     * Construct an instance of this <code>RuleSet</code> with the default
     * matching pattern prefix.
     */
    public CredentialHandlerRuleSet() {
        this("");
    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     */
    public CredentialHandlerRuleSet(String prefix) {
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     *
     * @param digester Digester instance to which the new Rule instances
     */
    @Override
    public void addRuleInstances(Digester digester) {
        StringBuilder pattern = new StringBuilder(prefix);
        for (int i = 0; i < MAX_NESTED_LEVELS; i++) {
            if (i > 0) {
                pattern.append('/');
            }
            pattern.append("CredentialHandler");
            addRuleInstances(digester, pattern.toString(), i == 0 ? "setCredentialHandler"
                    : "addCredentialHandler");
        }
    }

    /**
     * 添加CredentialHandler标签规则解析器
     * @param digester 规则管理器对象
     * @param pattern 匹配规则
     * @param methodName 方法名
     */
    private void addRuleInstances(Digester digester, String pattern, String methodName) {
        //创建CredentialHandler标签中的属性className值的对象
        digester.addObjectCreate(pattern, null /* MUST be specified in the element */,
                "className");
        //设置标签对象的属性值
        digester.addSetProperties(pattern);
        //将当前对象作为参数 执行父标签对象的方法
        digester.addSetNext(pattern, methodName, "org.apache.catalina.CredentialHandler");
    }
}
