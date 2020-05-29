/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Proxy;

import org.springframework.aop.SpringProxy;

/**
 * Default {@link AopProxyFactory} implementation, creating either a CGLIB proxy
 * or a JDK dynamic proxy.
 *
 * <p>Creates a CGLIB proxy if one the following is true for a given
 * {@link AdvisedSupport} instance:
 * <ul>
 * <li>the {@code optimize} flag is set
 * <li>the {@code proxyTargetClass} flag is set
 * <li>no proxy interfaces have been specified
 * </ul>
 *
 * <p>In general, specify {@code proxyTargetClass} to enforce a CGLIB proxy,
 * or specify one or more interfaces to use a JDK dynamic proxy.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 12.03.2004
 * @see AdvisedSupport#setOptimize
 * @see AdvisedSupport#setProxyTargetClass
 * @see AdvisedSupport#setInterfaces
 * todo  如果目标对象实现了接口，默认情况下会釆用JDK的动态代理实现AOP
 * todo 如果目标对象实现了接口，可以强制使用C GLIB实现AOP
 * todo 如果目标对象没有实现了接口，必须采用CGLiB库，Spring会自动在JDK动态代理 和CGLIB之间转换
 * todo (1 )添加 CGLIB 库，Spring_HOME/cglib/*.jar
 * todo (2 )在 Spring 配置文件中加入<aop:aspectj-autoproxy proxy-target-class=”true"/>
 * todo JDK动态代理和CGLIB字节码生成的区别
 * todo JDK动态代理只能对实现了接口的类生成代理，而不能针对类
 * todo CGLIB是针对类实现代理，主要是对指定的类生成一个子类，覆盖其中的方法，因为 是继承，所以该类或方法最好不要声明成final
 * todo public class MylnvocationHandler implements InvocationHandler 重新构造方法 invoke getProxy
 * todo Proxy.newProxylnstance(Thread.currentThread().getContextClassLoader(), target.getClass().getlnterfaces(). this);
 * implements Methodinterceptor
 * Enhancer enhancer ■ new Enhancer();
enhancer.setSuperclass(EnhancerDemo.class);
enhancer.setCallback(new Methodlnterceptorlmpl());
EnhancerDemo demo = (EnhancerDemo) enhancer.create(); demo.test();
 *
 */
@SuppressWarnings("serial")
public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {
	/**
	 * todo 创建代理对象
	 * @param config the AOP configuration in the form of an
	 * AdvisedSupport object
	 * @return
	 * @throws AopConfigException
	 */
	@Override
	public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
		if (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
			Class<?> targetClass = config.getTargetClass();
			if (targetClass == null) {
				throw new AopConfigException("TargetSource cannot determine target class: " +
						"Either an interface or a target is required for proxy creation.");
			}
			//todo 如果目标类是接口或者目标类是Proxy的子类，则使用JDK动态代理方式
			if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
				return new JdkDynamicAopProxy(config);
			}
			//todo 使用Cglib动态代理
			return new ObjenesisCglibAopProxy(config);
		}
		else {
			//todo 默认使用JDK动态代理
			return new JdkDynamicAopProxy(config);
		}
	}

	/**
	 * Determine whether the supplied {@link AdvisedSupport} has only the
	 * {@link org.springframework.aop.SpringProxy} interface specified
	 * (or no proxy interfaces specified at all).
	 */
	private boolean hasNoUserSuppliedProxyInterfaces(AdvisedSupport config) {
		Class<?>[] ifcs = config.getProxiedInterfaces();
		return (ifcs.length == 0 || (ifcs.length == 1 && SpringProxy.class.isAssignableFrom(ifcs[0])));
	}

}
