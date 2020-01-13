package org.akvo

import org.akvo.caddisfly.instruction.*
import org.akvo.caddisfly.internal.NavigationTest
import org.akvo.caddisfly.internal.StriptestInternal
import org.akvo.caddisfly.update.UpdateTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

// Runs tests that generate screenshots
@RunWith(Suite::class)
@Suite.SuiteClasses(
        BluetoothInstructions::class, CbtInstructions::class,
        ManualInstructions::class, PhotometerInstructions::class,
        SensorInstructions::class, StriptestInternal::class, TesterInstructions::class,
        NavigationTest::class, UpdateTest::class)
class ScreenshotSuite