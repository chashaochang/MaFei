package org.intellij.lang.annotations

@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.FUNCTION,
)
annotation class Language(val value: String)
