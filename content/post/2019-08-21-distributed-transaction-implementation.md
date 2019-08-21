---
slug: distributed-transaction-implementation
keywords:
- distributed transaction
- Saga
- 分布式事务
title: '分布式事务框架设计与实现'
description: 轻量级分布式事务框架实现；Lightweight distributed transaction framework implementation
date: 2019-08-21T21:45:39+08:00
draft: false
tags: []
categories:
- 10000 Hours
hiddenFromHomePage: false
hideHeaderAndFooter: false
enableOutdatedInfoWarning: false
---

今年年初建设交易中台，基于Saga模式，开发了一个轻量级的分布式事务框架。同时加入了全局锁以提高数据隔离性。
本文根据部门内部分享整理。

----

![](http://ww2.sinaimg.cn/large/006y8mN6gy1g67o723flej31he0u00wb.jpg)
![](http://ww2.sinaimg.cn/large/006y8mN6gy1g67oejtqnuj31he0u0adx.jpg)
![](http://ww4.sinaimg.cn/large/006y8mN6gy1g67oekf3tkj31hf0u0424.jpg)
![](http://ww4.sinaimg.cn/large/006y8mN6gy1g67oel096ej31hn0u043t.jpg)
![](http://ww4.sinaimg.cn/large/006y8mN6gy1g67oels4xtj31hg0u0ae3.jpg)
![](http://ww1.sinaimg.cn/large/006y8mN6gy1g67oembwnoj31h90u0tet.jpg)
![](http://ww4.sinaimg.cn/large/006y8mN6gy1g67oemyoxhj31ha0u0dml.jpg)
![](http://ww2.sinaimg.cn/large/006y8mN6gy1g67oenj2ynj31hk0u0gt6.jpg)
![](http://ww3.sinaimg.cn/large/006y8mN6gy1g67oeoby61j31hy0u0q9x.jpg)
![](http://ww2.sinaimg.cn/large/006y8mN6gy1g67oeoxxauj31hu0u0dnk.jpg)
![](http://ww3.sinaimg.cn/large/006y8mN6gy1g67oepls4uj31hl0u046r.jpg)
![](http://ww3.sinaimg.cn/large/006y8mN6gy1g67oeq7v64j31hl0u00uj.jpg)