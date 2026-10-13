// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorCalendar",
    platforms: [.iOS(.v16)],
    products: [
        .library(
            name: "CapacitorCalendar",
            targets: ["CalendarPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor.git", from: "9.0.0-alpha.7")
    ],
    targets: [
        .target(
            name: "CalendarPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor")
            ],
            path: "ios/Sources/CalendarPlugin"),
        .testTarget(
            name: "CalendarPluginTests",
            dependencies: ["CalendarPlugin"],
            path: "ios/Tests/CalendarPluginTests")
    ]
)
