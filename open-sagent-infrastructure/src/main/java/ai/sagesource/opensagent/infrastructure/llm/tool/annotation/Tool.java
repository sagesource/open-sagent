package ai.sagesource.opensagent.infrastructure.llm.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具方法注解
 * <p>
 * 标注在方法上，用于定义一个可调用工具的元信息
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    /**
     * 工具名称
     */
    String name();

    /**
     * 工具描述
     */
    String description() default "";
}
