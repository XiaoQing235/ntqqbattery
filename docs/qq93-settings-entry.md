# QQ 9.3 设置入口适配（待真机验证）

目标：解决 QQ 9.3.10.37675 中固定设置类名失配导致入口不显示的问题。
保留 NTQQBattery 自有 QQSettingsActivity、功能开关及 QQ 宿主内存储；不修改省电策略。

## 变更

- 增加 DexKit 2.3.0，在宿主 ClassLoader 中查找设置 provider 的特征字符串。
- 保留旧类名，补充 SimpleItemProcessor 完整类名，并验证构造与点击回调结构。
- 根据 provider 方法返回类型与 DEX 类/方法特征寻找混淆后的条目类。
- 对所有匹配 provider 去重注册；失败输出具体阶段日志。
- 仅 QQ 主进程执行入口定位；不持久化失败缓存，避免 QQ 更新后旧缓存阻断。
- 注入时复制原列表后设置返回值，兼容不可变 List，并追踪已插入分组避免重复。

## 来源与许可边界

参考 TSBattery 的 QQ 9.3 兼容思路：
https://github.com/fankes/TSBattery/blob/b1f561223b29f1b42a1488610ea9452ef0e7decb/app/src/main/java/com/fankes/tsbattery/hook/entity/QQTIMHooker.kt

TSBattery 使用 AGPLv3。本补丁的 resolver 是独立编写，不复制 TSBattery 实现，
也不把它的许可擅自授予 NTQQBattery 上游代码。上游 NTQQBattery 当前没有 LICENSE；
发布修改版之前应向其权利人确认修改及分发授权。

## 验证状态

- `git diff --check`：通过。
- 已添加三个反射形状单元测试，覆盖 List 子类、4/5 参数构造、缺失点击回调和不匹配构造。
- `:app:testDebugUnitTest :app:assembleDebug`：未能进入构建。Gradle 9.5.1 distribution 未缓存，执行环境网络/代理连接失败。
- 未在 QQ 9.3.10.37675 APK 上验证类命中，未在手机上运行；不能据此宣称修复成功。

## 真机回归清单

1. 只有一个 NTQQBattery 版本启用，重启 QQ，日志含 provider 安装数量和条目类。
2. 设置页存在且只出现一个入口；反复进出页面不重复添加。
3. 点击正常打开 QQSettingsActivity，返回 QQ 设置没有崩溃。
4. 修改一个无风险开关，完整重启 QQ 后值保留，确认使用原宿主配置。
5. QQ 旧版本回归；新版本升级后不读取旧的类定位缓存。
6. 核对 APK 签名：自行构建的调试包不能覆盖上游不同签名 APK，勿以清数据作为首选。
