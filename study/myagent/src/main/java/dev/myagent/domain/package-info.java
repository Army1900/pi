/**
 * 领域层:agent 的模型、事件,与(后续)领域服务、端口。
 *
 * <p>分层铁律:本层只依赖 JDK,不 import application / infrastructure;
 * 端口(repository / gateway)在本层定义接口,适配器在 infrastructure 实现 ——
 * 依赖箭头单向指向本层(依赖倒置:端口词汇属于谁,接口就归谁)。
 */
package dev.myagent.domain;
