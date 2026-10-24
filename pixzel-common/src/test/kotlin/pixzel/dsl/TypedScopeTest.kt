package pixzel.dsl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import pixzel.component.Element
import pixzel.component.Text
import pixzel.graphics.RecordingBackend
import pixzel.layout.FixedTrack
import pixzel.layout.WeightedTrack
import pixzel.layout.unit.dp
import pixzel.layout.unit.fillAvailable
import pixzel.platform.TestHost
import pixzel.text.FixedFontTextService
import pixzel.ui
import kotlin.time.Duration

class TypedScopeTest {

    @Test
    fun `row and column scopes distribute parent-specific weights without changing child identity`() {
        val host = TestHost.of(400, 240)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        try {
            lateinit var first: Text
            lateinit var last: Text
            lateinit var vertical: Element
            fun RowScope.populate() {
                first = text("First") {
                    layout {
                        width = fillAvailable
                        height = 20.dp
                    }
                }.weighted(1f)
                last = weight(text("Last") {
                    layout {
                        width = fillAvailable
                        height = 20.dp
                    }
                }, 3f)
            }
            ui.content {
                row("weighted", spacing = 8.dp) {
                    layout {
                        width = 400.dp
                        height = 40.dp
                    }
                    populate()
                }
                vertical = column("vertical", spacing = 10.dp) {
                    layout {
                        width = 100.dp
                        height = 200.dp
                    }
                    surface("top") {
                        layout {
                            width = fillAvailable
                            height = fillAvailable
                        }
                    }.weighted(1f)
                    surface("bottom") {
                        layout {
                            width = fillAvailable
                            height = fillAvailable
                        }
                    }.weighted(3f)
                }
            }
            ui.frame(Duration.ZERO)
            assertEquals(98f, first.surface.bounds.width)
            assertEquals(294f, last.surface.bounds.width)
            assertEquals(106f, last.surface.bounds.x)
            assertEquals(47.5f, vertical.children[0].surface.bounds.height)
            assertEquals(142.5f, vertical.children[1].surface.bounds.height)
            val generation = last.generation
            last.layout.weight = 1f
            ui.frame(Duration.ZERO)
            assertEquals(196f, last.surface.bounds.width)
            assertEquals(generation, last.generation)
        } finally {
            ui.close()
            host.close()
        }
    }

    @Test
    fun `grid scope cell placement uses typed tracks and rejects foreign-child parent data`() {
        val host = TestHost.of(400, 240)
        val ui = ui(host, RecordingBackend.of(), FixedFontTextService.of())
        try {
            lateinit var left: Element
            lateinit var right: Element
            lateinit var lower: Element
            lateinit var gridScope: GridScope
            lateinit var rowScope: RowScope
            ui.content {
                row {
                    rowScope = this
                    layout {
                        width = 400.dp
                        height = 20.dp
                    }
                }
                grid(listOf(FixedTrack(100.dp), WeightedTrack()), spacing = 8.dp) {
                    gridScope = this
                    layout {
                        width = 400.dp
                        height = 80.dp
                    }
                    left = surface {
                        layout {
                            width = fillAvailable
                            height = 20.dp
                        }
                    }.inCell(0, 0)
                    right = cell(surface {
                        layout {
                            width = fillAvailable
                            height = 20.dp
                        }
                    }, 0, 1)
                    lower = surface {
                        layout {
                            width = fillAvailable
                            height = 32.dp
                        }
                    }.inCell(1, 1)
                }
            }
            ui.frame(Duration.ZERO)
            assertEquals(100f, left.surface.bounds.width)
            assertEquals(108f, right.surface.bounds.x)
            assertEquals(292f, right.surface.bounds.width)
            assertEquals(108f, lower.surface.bounds.x)
            assertEquals(28f, lower.surface.bounds.y)
            assertThrows(IllegalArgumentException::class.java) { rowScope.weight(right, 1f) }
            assertThrows(IllegalArgumentException::class.java) { gridScope.cell(right, 0, 2) }
            assertThrows(IllegalArgumentException::class.java) { gridScope.cell(ui.root, 0, 0) }
            assertThrows(IllegalArgumentException::class.java) { rowScope.weight(right, Float.NaN) }
        } finally {
            ui.close()
            host.close()
        }
    }
}
