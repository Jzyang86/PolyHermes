package com.wrbug.polymarketbot.util

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * BigDecimal乘法扩展函数
 * 安全地将BigDecimal与任意数值类型相乘
 * @param value 乘数，支持BigDecimal、BigInteger、Number类型或可转换为BigDecimal的字符串
 * @return 乘法结果，如果转换失败返回BigDecimal.ZERO
 */
fun BigDecimal.multi(value: Any): BigDecimal {
    kotlin.runCatching {
        if (value is BigDecimal) {
            return multiply(value)
        }
        if (value is BigInteger) {
            return multiply(value.toBigDecimal())
        }
        if (value is Number) {
            return multiply(value.toSafeBigDecimal())
        }
        return multiply(BigDecimal(value.toString()))
    }
    return BigDecimal.ZERO
}

/**
 * BigDecimal除法扩展函数
 * 安全地将BigDecimal与任意数值类型相除
 * @param value 除数，支持BigDecimal、BigInteger类型或可转换为BigDecimal的字符串
 * @return 除法结果，精度为18位小数，使用四舍五入模式，如果转换失败返回IllegalBigDecimal
 */
fun BigDecimal.div(value: Any): BigDecimal {
    kotlin.runCatching {
        if (value is BigDecimal) {
            return divide(value, 18, RoundingMode.HALF_UP).stripTrailingZeros()
        }
        if (value is BigInteger) {
            return divide(value.toSafeBigDecimal(), 18, RoundingMode.HALF_UP).stripTrailingZeros()
        }
        return divide(BigDecimal(value.toString()), 18, RoundingMode.HALF_UP).stripTrailingZeros()
    }
    return IllegalBigDecimal
}

/**
 * BigInteger乘法扩展函数
 * 将BigInteger转换为BigDecimal后与任意数值类型相乘
 * @param value 乘数，支持任意可转换为BigDecimal的类型
 * @return 乘法结果，如果转换失败返回IllegalBigDecimal
 */
fun BigInteger.multi(value: Any): BigDecimal {
    val v = this.toBigDecimal()
    return runCatching {
        v.multi(value)
    }.getOrDefault(IllegalBigDecimal)
}

/**
 * 大于比较扩展函数
 * 安全地比较两个任意类型的数值大小
 * 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
 * @param target 比较目标值
 * @return 如果当前值大于目标值返回true，否则返回false（null值返回false）
 */
fun Any?.gt(target: Any?): Boolean {
    if (this == null || target == null) {
        return false
    }
    val thisValue = this.toSafeBigDecimal()
    val targetValue = target.toSafeBigDecimal()
    // 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
    return thisValue > targetValue
}

/**
 * 大于等于比较扩展函数
 * 安全地比较两个任意类型的数值大小
 * 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
 * @param target 比较目标值
 * @return 如果当前值大于等于目标值返回true，否则返回false（null值返回false）
 */
fun Any?.gte(target: Any?): Boolean {
    if (this == null || target == null) {
        return false
    }
    val thisValue = this.toSafeBigDecimal()
    val targetValue = target.toSafeBigDecimal()
    // 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
    return thisValue.compareTo(targetValue) >= 0
}

/**
 * 小于比较扩展函数
 * 安全地比较两个任意类型的数值大小
 * 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
 * @param target 比较目标值
 * @return 如果当前值小于目标值返回true，否则返回false（null值返回false）
 */
fun Any?.lt(target: Any?): Boolean {
    if (this == null || target == null) {
        return false
    }
    val thisValue = this.toSafeBigDecimal()
    val targetValue = target.toSafeBigDecimal()
    // 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
    return thisValue < targetValue
}

/**
 * 小于等于比较扩展函数
 * 安全地比较两个任意类型的数值大小
 * 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
 * @param target 比较目标值
 * @return 如果当前值小于等于目标值返回true，否则返回false（null值返回false）
 */
fun Any?.lte(target: Any?): Boolean {
    if (this == null || target == null) {
        return false
    }
    val thisValue = this.toSafeBigDecimal()
    val targetValue = target.toSafeBigDecimal()
    // 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
    return thisValue.compareTo(targetValue) <= 0
}

/**
 * 等于比较扩展函数
 * 安全地比较两个任意类型的数值是否相等
 * 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
 * @param target 比较目标值
 * @return 如果当前值等于目标值返回true，否则返回false（null值返回false）
 */
fun Any?.eq(target: Any?): Boolean {
    if (this == null || target == null) {
        return false
    }
    val thisValue = this.toSafeBigDecimal()
    val targetValue = target.toSafeBigDecimal()
    // 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
    // 例如："0.0" 和 "0" 在数值上相等，但 scale 不同
    return thisValue.compareTo(targetValue) == 0
}

/**
 * 不等于比较扩展函数
 * 安全地比较两个任意类型的数值是否不相等
 * 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
 * @param target 比较目标值
 * @return 如果当前值不等于目标值返回true，否则返回false（null值返回false）
 */
fun Any?.neq(target: Any?): Boolean {
    if (this == null || target == null) {
        return false
    }
    val thisValue = this.toSafeBigDecimal()
    val targetValue = target.toSafeBigDecimal()
    // 使用 compareTo 方法比较，避免 BigDecimal 的 scale 问题
    return thisValue.compareTo(targetValue) != 0
}

