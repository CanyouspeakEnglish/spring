/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.context.weaving;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.aspectj.weaver.loadtime.ClassPreProcessorAgentAdapter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.instrument.classloading.InstrumentationLoadTimeWeaver;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.lang.Nullable;

/**
 * Post-processor that registers AspectJ's
 * {@link org.aspectj.weaver.loadtime.ClassPreProcessorAgentAdapter}
 * with the Spring application context's default
 * {@link org.springframework.instrument.classloading.LoadTimeWeaver}.
 *
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @since 2.5
 * todo 实现LoadTimeWeaverAware
 * 当在 Spring 中调用AspectjWeavingEnabler时，this.loadTimeWeaver 尚未被初始化，那么，会
 * 直接调用beanFactory.getBean方法获取对应的defaultContextLoadTimeWeaver类型的bean,并将其设置为AspectjWeavingEnabler
 * 类型 bean的loadTimeWeaver属性中。当然 AspecJWeavingEnabler同样实现了BeanClassLoaderAware 以及 Ordered 接口，
 * 实现 BeanClassLoaderAware接口保证了在 bean 初始化的时候调用 AbstractAutowireCapableBeanFactory 的 invokeAwareMethods
 * 的时候将 beanClassLoader赋值给当前类。而实现Ordered接口则保证在实例化bean时当前bean会被最先初 始化
 */
public class AspectJWeavingEnabler
		implements BeanFactoryPostProcessor, BeanClassLoaderAware, LoadTimeWeaverAware, Ordered {

	public static final String ASPECTJ_AOP_XML_RESOURCE = "META-INF/aop.xml";


	@Nullable
	private ClassLoader beanClassLoader;
	/**
	 * todo spring初始化成 DefaultContextLoadTimeWeaver
	 */
	@Nullable
	private LoadTimeWeaver loadTimeWeaver;


	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	@Override
	public void setLoadTimeWeaver(LoadTimeWeaver loadTimeWeaver) {
		this.loadTimeWeaver = loadTimeWeaver;
	}

	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE;
	}

	/**
	 * todo 因为AspectjWeavingEnabler 实现了 BeanFactoryPostProcessor 当所有Bean解析结束后会调用postProcessBeanFactory方法
	 * @param beanFactory the bean factory used by the application context
	 * @throws BeansException
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		enableAspectJWeaving(this.loadTimeWeaver, this.beanClassLoader);
	}


	/**
	 * Enable AspectJ weaving with the given {@link LoadTimeWeaver}.
	 * @param weaverToUse the LoadTimeWeaver to apply to (or {@code null} for a default weaver)
	 * @param beanClassLoader the class loader to create a default weaver for (if necessary)
	 */
	public static void enableAspectJWeaving(
			@Nullable LoadTimeWeaver weaverToUse, @Nullable ClassLoader beanClassLoader) {

		if (weaverToUse == null) {
			//todo 此时已经被初始化为DefaultContextLoadTimeWeaver
			if (InstrumentationLoadTimeWeaver.isInstrumentationAvailable()) {
				weaverToUse = new InstrumentationLoadTimeWeaver(beanClassLoader);
			}
			else {
				throw new IllegalStateException("No LoadTimeWeaver available");
			}
		}
		//todo 使用DefaultContextLoadTimeWeaver类型的bean中的loadtimeweaver属性注册转换器
		weaverToUse.addTransformer(
				new AspectJClassBypassingClassFileTransformer(new ClassPreProcessorAgentAdapter()));
	}


	/**
	 * ClassFileTransformer decorator that suppresses processing of AspectJ
	 * classes in order to avoid potential LinkageErrors.
	 * @see org.springframework.context.annotation.LoadTimeWeavingConfiguration
	 * todo 仅仅是告诉 AspectJ 以 org.aspectj 开头的 或者org/aspectj开头的类不进行处理
	 */
	private static class AspectJClassBypassingClassFileTransformer implements ClassFileTransformer {

		private final ClassFileTransformer delegate;

		public AspectJClassBypassingClassFileTransformer(ClassFileTransformer delegate) {
			this.delegate = delegate;
		}

		@Override
		public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
				ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

			if (className.startsWith("org.aspectj") || className.startsWith("org/aspectj")) {
				return classfileBuffer;
			}
			//todo 委托给AspectJ代理继续处理
			return this.delegate.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
		}
	}

}
