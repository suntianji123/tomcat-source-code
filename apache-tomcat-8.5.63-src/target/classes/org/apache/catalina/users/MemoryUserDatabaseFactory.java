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


package org.apache.catalina.users;


import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;


/**
 * 生产用户数据库资源的工厂类
 */
public class MemoryUserDatabaseFactory implements ObjectFactory {


    // --------------------------------------------------------- Public Methods


    /**
     * 根据引用对象  生产一个内存用户数据库
     * @param obj 引用对象
     * @param name NamingEntry在NamingContext上下文的索引名对象 UserDatabase包装后的对象
     * @param nameCtx NamingContext对象
     * @param environment NamingContext对象的环境表
     * @return
     * @throws Exception
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable<?,?> environment)
        throws Exception {

        // We only know how to deal with <code>javax.naming.Reference</code>s
        // that specify a class name of "org.apache.catalina.UserDatabase"
        if ((obj == null) || !(obj instanceof Reference)) {//绑定到NamingContext的对象不是引用类型 直接返回
            return null;
        }

        //获取引用对象
        Reference ref = (Reference) obj;
        if (!"org.apache.catalina.UserDatabase".equals(ref.getClassName())) {//className类型 不是用户数据库类型  直接返回
            return null;
        }

        // Create and configure a MemoryUserDatabase instance based on the
        // RefAddr values associated with this Reference
        //实例化一个内存用户数据库对象
        MemoryUserDatabase database = new MemoryUserDatabase(name.toString());

        //引用地址对象
        RefAddr ra = null;

        //获取路径
        ra = ref.get("pathname");
        if (ra != null) {
            //设置内存数据库的路径
            database.setPathname(ra.getContent().toString());
        }

        //数据库是否为只读
        ra = ref.get("readonly");
        if (ra != null) {
            database.setReadonly(Boolean.parseBoolean(ra.getContent().toString()));
        }

        ra = ref.get("watchSource");
        if (ra != null) {
            database.setWatchSource(Boolean.parseBoolean(ra.getContent().toString()));
        }

        // 打开内存数据库
        database.open();
        // Don't try something we know won't work
        if (!database.getReadonly())//不是只读数据库
            //执行数据库的保存方法
            database.save();
        return database;

    }


}
