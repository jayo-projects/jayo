/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.external;

import java.lang.annotation.*;

/**
 * A common Jayo annotation to declare that annotated elements cannot be non-negative.
 * <p>
 * Should be used at parameter, return value, and field level. Method overrides should repeat parent
 * {@code @NonNegative} annotations unless they behave differently.
 */
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NonNegative {
}
