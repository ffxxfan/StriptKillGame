package com.example.striptkillgamedemo2.ai.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DmToolRegistryTest {

    /** Minimal fake DmTool for testing. */
    static class FakeTool implements DmTool {
        private final String name;
        private final String description;

        FakeTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override public String name() { return name; }
        @Override public String description() { return description; }
        @Override public Class<?> inputType() { return FakeInput.class; }
        @Override public Object execute(Object input, DmToolContext ctx) { return "ok"; }

        @lombok.Data
        static class FakeInput { private String value; }
    }

    private DmToolRegistry registry;
    private DmToolContext ctx;

    @BeforeEach
    void setUp() {
        List<DmTool> tools = List.of(
                new FakeTool("toolA", "Tool A description"),
                new FakeTool("toolB", "Tool B description"),
                new FakeTool("toolC", "Tool C description")
        );
        registry = new DmToolRegistry(tools, mock(SimpMessagingTemplate.class));
        ctx = DmToolContext.builder().build();
    }

    @Test
    void buildCallbacks_shouldCreateOneCallbackPerTool() {
        List<ToolCallback> callbacks = registry.buildCallbacks(ctx);
        assertEquals(3, callbacks.size(), "Should produce one callback per tool");
    }

    @Test
    void buildCallbacks_callbacksShouldHaveCorrectNames() {
        List<ToolCallback> callbacks = registry.buildCallbacks(ctx);
        List<String> callbackNames = callbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .toList();
        assertTrue(callbackNames.contains("toolA"));
        assertTrue(callbackNames.contains("toolB"));
        assertTrue(callbackNames.contains("toolC"));
    }

    @Test
    void getToolNames_shouldReturnAllNames() {
        List<String> names = registry.getToolNames();
        assertEquals(3, names.size());
        assertTrue(names.contains("toolA"));
        assertTrue(names.contains("toolB"));
        assertTrue(names.contains("toolC"));
    }

    @Test
    void buildCallbacks_withEmptyToolList_shouldReturnEmpty() {
        DmToolRegistry emptyRegistry = new DmToolRegistry(List.of(), mock(SimpMessagingTemplate.class));
        List<ToolCallback> callbacks = emptyRegistry.buildCallbacks(ctx);
        assertTrue(callbacks.isEmpty());
    }
}
