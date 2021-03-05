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

import java.util.HashMap;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;

/**
 * 设置标签对象所有属性的类
 */
public class SetAllPropertiesRule extends Rule {


    // ----------------------------------------------------------- Constructors
    public SetAllPropertiesRule() {}

    /**
     * 实例化一个设置标签对象所有属性的类 并指定需要排除的属性
     * @param exclude 需要排除的属性数组
     */
    public SetAllPropertiesRule(String[] exclude) {
        for (String s : exclude) if (s != null) this.excludes.put(s, s);
    }

    /**
     * 被排除的属性值
     */
    protected final HashMap<String,String> excludes = new HashMap<>();

    // --------------------------------------------------------- Public Methods


    /**
     * 设置所有属性的标签
     * @param namespace 命名空间
     * @param nameX 标签名
     * @param attributes 属性列表
     *
     * @throws Exception
     */
    @Override
    public void begin(String namespace, String nameX, Attributes attributes)
        throws Exception {

        for (int i = 0; i < attributes.getLength(); i++) {//遍历属性里诶包
            //获取属性名
            String name = attributes.getLocalName(i);
            if ("".equals(name)) {
                name = attributes.getQName(i);
            }

            //获取属性值
            String value = attributes.getValue(i);

            //设置标签对象的属性值
            if ( !excludes.containsKey(name)) {
                if (!digester.isFakeAttribute(digester.peek(), name)
                        && !IntrospectionUtils.setProperty(digester.peek(), name, value)
                        && digester.getRulesValidation()) {
                    digester.getLogger().warn("[SetAllPropertiesRule]{" + digester.getMatch() +
                            "} Setting property '" + name + "' to '" +
                            value + "' did not find a matching property.");
                }
            }
        }

    }


}
