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

import org.apache.catalina.Context;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;

/**
 * 将当前标签对象作为参数执行父标签的规则类
 */
public class SetNextNamingRule extends Rule {


    // ----------------------------------------------------------- Constructors


    /**
     * Construct a "set next" rule with the specified method name.
     *
     * @param methodName Method name of the parent method to call
     * @param paramType Java class of the parent method's argument
     *  (if you wish to use a primitive type, specify the corresponding
     *  Java wrapper class instead, such as <code>java.lang.Boolean</code>
     *  for a <code>boolean</code> parameter)
     */
    public SetNextNamingRule(String methodName,
                       String paramType) {

        this.methodName = methodName;
        this.paramType = paramType;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The method name to call on the parent object.
     */
    protected final String methodName;


    /**
     * The Java class name of the parameter type expected by the method.
     */
    protected final String paramType;


    // --------------------------------------------------------- Public Methods


    /**
     * 结束标签方法
     * @param namespace 命名空间
     * @param name 标签名
     * @throws Exception
     */
    @Override
    public void end(String namespace, String name) throws Exception {

        //当前标签对象
        Object child = digester.peek(0);
        //父标签对象
        Object parent = digester.peek(1);

        NamingResourcesImpl namingResources = null;
        if (parent instanceof Context) {
            namingResources = ((Context) parent).getNamingResources();
        } else {
            //将父对象 强转为NamingResourcesImpl类型
            namingResources = (NamingResourcesImpl) parent;
        }

        //将当前标签对象作为参数 执行父标签对象的方法
        IntrospectionUtils.callMethod1(namingResources, methodName,
                child, paramType, digester.getClassLoader());
    }


    /**
     * Render a printable version of this Rule.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SetNextRule[");
        sb.append("methodName=");
        sb.append(methodName);
        sb.append(", paramType=");
        sb.append(paramType);
        sb.append(']');
        return sb.toString();
    }


}
