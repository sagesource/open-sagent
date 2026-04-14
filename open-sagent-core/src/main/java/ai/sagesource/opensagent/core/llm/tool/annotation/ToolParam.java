package ai.sagesource.opensagent.core.llm.tool.annotation;

import ai.sagesource.opensagent.core.llm.tool.ToolParameterType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数注解
 * <p>
 * 标注在方法参数上，定义参数在Tool Schema中的元信息
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {

    /**
     * 参数名称（默认使用方法参数名，但编译后可能丢失，建议显式指定）
     */
    String name() default "";

    /**
     * 参数描述
     */
    String description() default "";

    /**
     * 参数类型（对应JSON Schema类型）
     */
    ToolParameterType type() default ToolParameterType.STRING;

    /**
     * 是否必填
     */
    boolean required() default true;

    /**
     * 枚举值（当参数为枚举类型时使用）
     */
    String[] enumValues() default {};
}
