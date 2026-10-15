package cn.mafei.cmp.player.testkit

import cn.mafei.cmp.player.api.*

/** Deterministic, single-test-thread fake. Never automatically claims native readiness. */
class FakePlayerRuntime(override val capabilities: PlayerCapabilities = PlayerCapabilities()) : PlayerRuntime {
    private var sink: ((RuntimeEvent) -> Unit)? = null
    private val recorded = mutableListOf<RuntimeCommand>()
    val commands: List<RuntimeCommand> get() = recorded.toList()
    var beforeExecute: (RuntimeCommand) -> Unit = {}
    override fun bind(eventSink: (RuntimeEvent) -> Unit) { check(sink == null); sink = eventSink }
    override fun execute(command: RuntimeCommand) { recorded += command; beforeExecute(command) }
    fun emit(event: RuntimeEvent) { checkNotNull(sink)(event) }
    fun clearCommands() { recorded.clear() }
}
