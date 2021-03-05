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
package org.apache.naming;

import javax.naming.StringRefAddr;

/**
 * Represents a reference address to a resource.
 *
 * @author Remy Maucherat
 */
public class ResourceRef extends AbstractRef {

    private static final long serialVersionUID = 1L;


    /**
     * Default factory for this reference.
     */
    public static final String DEFAULT_FACTORY =
            org.apache.naming.factory.Constants.DEFAULT_RESOURCE_FACTORY;


    /**
     * Description address type.
     */
    public static final String DESCRIPTION = "description";


    /**
     * Scope address type.
     */
    public static final String SCOPE = "scope";


    /**
     * Auth address type.
     */
    public static final String AUTH = "auth";


    /**
     * Is this resource a singleton
     */
    public static final String SINGLETON = "singleton";


    /**
     * 实例化资源引用对象
     * @param resourceClass 资源对象的class全类名
     * @param description 资源描述
     * @param scope 资源范围
     * @param auth 认证
     * @param singleton 资源是否为单例
     */
    public ResourceRef(String resourceClass, String description,
                       String scope, String auth, boolean singleton) {
        this(resourceClass, description, scope, auth, singleton, null, null);
    }


    /**
     * 实例化一个资源引用对象
     * @param resourceClass 资源的全类名
     * @param description 资源描述
     * @param scope 资源范围
     * @param auth 资源认证
     * @param singleton 是否为单例
     * @param factory 生场资源对象的工厂
     * @param factoryLocation 工厂位置
     */
    public ResourceRef(String resourceClass, String description,
                       String scope, String auth, boolean singleton,
                       String factory, String factoryLocation) {
        super(resourceClass, factory, factoryLocation);
        //引用地址
        StringRefAddr refAddr = null;
        //描述不为null
        if (description != null) {
            refAddr = new StringRefAddr(DESCRIPTION, description);
            //添加字符串描述
            add(refAddr);
        }
        if (scope != null) {
            //范围不为null  添加引用范围
            refAddr = new StringRefAddr(SCOPE, scope);
            add(refAddr);
        }
        if (auth != null) {
            //认证不为null 添加认证
            refAddr = new StringRefAddr(AUTH, auth);
            add(refAddr);
        }
        // 添加是否为单利
        refAddr = new StringRefAddr(SINGLETON, Boolean.toString(singleton));
        add(refAddr);
    }


    @Override
    protected String getDefaultFactoryClassName() {
        return DEFAULT_FACTORY;
    }
}
