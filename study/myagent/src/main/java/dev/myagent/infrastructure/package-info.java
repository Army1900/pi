/**
 * 基础设施层:适配器,只实现 domain 端口,不反向被依赖(infrastructure → domain 单向)。
 *
 * <p>组装(组合根)在测试 setup / Main 手写构造函数 —— 不引 Spring,每条依赖边肉眼可见。
 */
package dev.myagent.infrastructure;
