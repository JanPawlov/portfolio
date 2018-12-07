
#### 1. [Bluetooth/](https://github.com/JanPawlov/portfolio/tree/master/Bluetooth)

BluetoothTaskExecutor in conjunction with BluetoothService are classes that are managing connections& actions with multiple Bluetooth Devices concurrently. Their statuses and available actions are displayed on simple UI views. Modular structure, totally independent from UI and scalable to managing up to 7 devices simultaneously (subject to phone manufacturers hardware limitations)


#### 2. [anim/](https://github.com/JanPawlov/portfolio/tree/master/anim)
Animation shown in the [video](https://github.com/JanPawlov/portfolio/blob/master/anim/anim.mp4) is the result of reverse engineering case. User can choose workout type by swipe/fling gestures. While he does it, background image alphas and circular icons sizes are changed accordingly. 
The view is swappable, meaning it will scroll to the nearest workout when user releases his finger. Scroll events can be passed to the parent view (via ScrollObserver interface) to add another effects (in this instance, background image alphas). User can also swipe up for details. This results in a dialog with blurred background.

#### 3. [Drones](https://github.com/JanPawlov/GameOfDronesApp "Game Of Drones")

Android application created during [HackZurich 2018](https://digitalfestival.ch/en/HACK/) that tackled the problem of autonomous drone air traffic. Application displayed live air traffic over Switzerland on the map; in addition to that, it introduced a concept of algorithm for drones that would move autonomously and be on collision course with each other. Drones shared a network in which they posted their current 3D location. Then, if a drone detected it was within close peremiter of other drone, they adjusted their height to avoid collision. Mocked drone movement was displayed in [unity application](https://github.com/psykulsk/GameOfDrones_unity) on the camera feed, showing avoidance alhorithm resulting in drones changing their flight heights, resulting in a nice visual effect. 
This project won the SwissCom workshop main prize.

#### 4. Architecture libraries
[JavaMVP](https://github.com/ApploverSoftware/JavaMVP)

[KotlinMVP](https://github.com/ApploverSoftware/KotlinMVP)

#### 5. [Interval Range Bar](https://github.com/JanPawlov/IntervalRangeBar)


