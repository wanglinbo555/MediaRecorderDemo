## MediaRecorderDemo
4路摄像头录制 MediaRecorder+TextureView+setMaxDuration 后台服务+悬浮窗+定时长循环+存储自动清理 基于高通

## 1.项目简介
出租车项目，行车记录仪部分，需求：4路摄像头同录，后台运行，可调出预览画面，定时长循环录制，存储满自动清理。

## 2.功能实现
服务悬浮窗：Service+View+TextureView，可将4宫格预览画面缩放、显示、隐藏；   
循环定长录像：TextureView+MediaRecorder和setMaxDuration +setOnInfoListener+开关录像；    
存储清理：cleanSDservice

## 3.踩坑点
(1)TextureView和SurfaceView的区别？

答：TextureView支持缩放位移隐藏等操作，SurfaceView是独立view，不依赖于父控件，本项目中通过隐藏SurfaceView所在的父控件，不会隐藏SurfaceView，
通过隐藏SurfaceView本身，则会触发其surfaceDestroyed回调销毁，与需求不一致。但是发现性能上TextureView占用的内存更高。

(2)高通平台分辨率的设置和其他平台差异

答：发现谷歌Api设置分辨率的方法在高通平台设置后再获取，没有立即生效。最后加上parameters.set("video-size", VIDEO_WIDTH[index] + "x" + VIDEO_HIGTH[index]);才生效；

(3)录像开启失败

答:预览和录像的分辨率设置不一致且要摄像头支持，或MediaRecorder配置设置顺序有误，或设备是否支持多路音频录制。

(4)多路录制和单路录制差异

答：所有问题，单路测试无问题，单路录制运行正常的代码集成到多路录制，发现部分接口运行不生效，这个是平台区别于手机做出了部分自定义修改，手机只有前后置摄像头，且只能开启一路。

(5)其他系统底层问题

答：以下均由framerwork和HAL层解决的问题，涉及同时开启录像报错，画面抖动，多路音频同录报错。

(6)录像时长1分钟，4路录制2个小时后，服务停止。

答：MediaRecorder频繁的开关，底层报错（open too many file 句柄超过1024），导致多媒体服务死机停止，问题定位到句柄增加，且可复现，但底层如何优化处理仍未未出方案，留待解决。

(7)摄像头打开黑屏

答：摄像头资源被占用，关闭其他摄像头应用，底层提供主动释放方法。

## 4.不足之处
MediaRecorder开关来处理定时长存储文件，由于资源释放开启都需要时间，且4路录制，导致录制的前后2个视频间隔有5秒左右，存在漏秒问题，并且频繁的快关，调用setOutputFile，句柄未释放，最后累加，导致服务停止。

## 5.改进之处
录像的同时还要兼顾拍照，人脸识别，直播等功能，其中涉及到功能之前要不同分辨率的问题；
录像漏秒问题，文件切割是否可以放到底层去做
采用硬编码方式录像 mediacodec+GLSurfaceView+MediaMuxer

## 6.相关链接
[https://blog.csdn.net/wang1lin2bo2/article/details/102400398](https://blog.csdn.net/wang1lin2bo2/article/details/102400398)



