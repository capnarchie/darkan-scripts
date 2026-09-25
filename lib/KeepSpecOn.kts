class KeepSpecOn : BotScript() {
    override suspend fun loop() {
        if (!usingSpec() && specPercent() >= 25) {
            clickButton(884, 4)
            delayUntil(2000) { usingSpec() }
        }
    }
}
