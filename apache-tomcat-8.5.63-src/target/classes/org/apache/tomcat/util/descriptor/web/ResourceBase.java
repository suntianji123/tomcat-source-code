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
package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;


/**
 * Representation of an Context element
 *
 * @author Peter Rossbach (pero@apache.org)
 */
public class ResourceBase implements Serializable, Injectable {

    private static final long serialVersionUID = 1L;


    // ------------------------------------------------------------- Properties

    /**
     * 资源描述： 例如：User database that can be updated and saved
     */
    private String description = null;

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * 资源名 例如：UserDatabase
     */
    private String name = null;

    @Override
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }


    /**
     * 资源的Class类 例如：org.apache.catalina.UserDatabase
     */
    private String type = null;

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }


    /**
     * 资源对象的查询名
     */
    private String lookupName = null;

    public String getLookupName() {
        return lookupName;
    }

    public void setLookupName(String lookupName) {
        if (lookupName == null || lookupName.length() == 0) {
            this.lookupName = null;
            return;
        }
        this.lookupName = lookupName;
    }


    /**
     * 属性map  例如{factory :org.apache.catalina.users.MemoryUserDatabaseFactory,pathname:conf/tomcat-users.xml}
     *
     */
    private final HashMap<String, Object> properties = new HashMap<>();

    /**
     * @param name The property name
     * @return a configured property.
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Set a configured property.
     * @param name The property name
     * @param value The property value
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Remove a configured property.
     * @param name The property name
     */
    public void removeProperty(String name) {
        properties.remove(name);
    }

    /**
     * 获取资源对象的属性列表
     * @return
     */
    public Iterator<String> listProperties() {
        return properties.keySet().iterator();
    }

    private final List<InjectionTarget> injectionTargets = new ArrayList<>();

    @Override
    public void addInjectionTarget(String injectionTargetName, String jndiName) {
        InjectionTarget target = new InjectionTarget(injectionTargetName, jndiName);
        injectionTargets.add(target);
    }

    @Override
    public List<InjectionTarget> getInjectionTargets() {
        return injectionTargets;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((injectionTargets == null) ? 0 : injectionTargets.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((properties == null) ? 0 : properties.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((lookupName == null) ? 0 : lookupName.hashCode());
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ResourceBase other = (ResourceBase) obj;
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (injectionTargets == null) {
            if (other.injectionTargets != null) {
                return false;
            }
        } else if (!injectionTargets.equals(other.injectionTargets)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (properties == null) {
            if (other.properties != null) {
                return false;
            }
        } else if (!properties.equals(other.properties)) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        if (lookupName == null) {
            if (other.lookupName != null) {
                return false;
            }
        } else if (!lookupName.equals(other.lookupName)) {
            return false;
        }
        return true;
    }


    /**
     * 所属的名字资源对象
     */
    private NamingResources resources = null;

    public NamingResources getNamingResources() {
        return this.resources;
    }

    public void setNamingResources(NamingResources resources) {
        this.resources = resources;
    }
}
