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

import java.util.Iterator;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

/**
 * 命名Context绑定枚举类
 */
public class NamingContextBindingsEnumeration
    implements NamingEnumeration<Binding> {


    // ----------------------------------------------------------- Constructors


    /**
     * 实例化一个命名Context绑定枚举类
     */
    public NamingContextBindingsEnumeration(Iterator<NamingEntry> entries,
            Context ctx) {
        //命名实体迭代器对象
        iterator = entries;

        //NamingContext对象
        this.ctx = ctx;
    }

    // -------------------------------------------------------------- Variables


    /**
     * 名字实体迭代器
     */
    protected final Iterator<NamingEntry> iterator;


    /**
     * NamingContext对象
     */
    private final Context ctx;


    // --------------------------------------------------------- Public Methods


    /**
     * 獲取NamingEntry迭代器的下一個元素
     * @return
     * @throws NamingException
     */
    @Override
    public Binding next()
        throws NamingException {
        return nextElementInternal();
    }


    /**
     * 判断是否还有下一个名字实体对象
     * @return
     * @throws NamingException
     */
    @Override
    public boolean hasMore()
        throws NamingException {
        //实体列表迭代器有下一个元素
        return iterator.hasNext();
    }


    /**
     * Closes this enumeration.
     */
    @Override
    public void close()
        throws NamingException {
    }


    @Override
    public boolean hasMoreElements() {
        return iterator.hasNext();
    }


    @Override
    public Binding nextElement() {
        try {
            return nextElementInternal();
        } catch (NamingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 获取下一个NamingEntry对应的Bingding对象
     * @return
     * @throws NamingException
     */
    private Binding nextElementInternal() throws NamingException {
        //获取命名实体对象
        NamingEntry entry = iterator.next();
        //结果
        Object value;

        // If the entry is a reference, resolve it
        if (entry.type == NamingEntry.REFERENCE
                || entry.type == NamingEntry.LINK_REF) {//引用类型
            try {

                value = ctx.lookup(new CompositeName(entry.name));
            } catch (NamingException e) {
                throw e;
            } catch (Exception e) {
                NamingException ne = new NamingException(e.getMessage());
                ne.initCause(e);
                throw ne;
            }
        } else {
            value = entry.value;
        }

        return new Binding(entry.name, value.getClass().getName(), value, true);
    }
}

