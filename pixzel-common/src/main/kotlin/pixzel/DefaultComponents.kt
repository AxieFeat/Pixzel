package pixzel

import pixzel.component.Button
import pixzel.component.Checkbox
import pixzel.component.ComponentFactory
import pixzel.component.Image
import pixzel.component.Progress
import pixzel.component.Radio
import pixzel.component.ScrollBar
import pixzel.component.ScrollView
import pixzel.component.SimpleButton
import pixzel.component.SimpleCheckbox
import pixzel.component.SimpleImage
import pixzel.component.SimpleProgress
import pixzel.component.SimpleRadio
import pixzel.component.SimpleScrollBar
import pixzel.component.SimpleScrollView
import pixzel.component.SimpleSlider
import pixzel.component.SimpleTabs
import pixzel.component.SimpleText
import pixzel.component.SimpleTextField
import pixzel.component.SimpleToggle
import pixzel.component.Slider
import pixzel.component.Tabs
import pixzel.component.Text
import pixzel.component.TextField
import pixzel.component.Toggle
import pixzel.surface.SimpleSurfaceElement
import pixzel.surface.SurfaceElement

internal class DefaultComponents : ComponentFactory {

    override fun surface(id: String): SurfaceElement = SimpleSurfaceElement(id)
    override fun text(id: String): Text = SimpleText(id)
    override fun button(id: String): Button = SimpleButton(id, this)
    override fun textField(id: String): TextField = SimpleTextField(id)
    override fun scrollView(id: String): ScrollView = SimpleScrollView(id)
    override fun image(id: String): Image = SimpleImage(id)
    override fun checkbox(id: String): Checkbox = SimpleCheckbox(id, this)
    override fun toggle(id: String): Toggle = SimpleToggle(id, this)
    override fun radio(id: String): Radio = SimpleRadio(id, this)
    override fun slider(id: String): Slider = SimpleSlider(id)
    override fun progress(id: String): Progress = SimpleProgress(id)
    override fun tabs(id: String): Tabs = SimpleTabs(id, this)
    override fun scrollBar(id: String): ScrollBar = SimpleScrollBar(id)
}
