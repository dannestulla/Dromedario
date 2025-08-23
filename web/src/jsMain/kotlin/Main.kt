import androidx.compose.runtime.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

fun main() {
    renderComposable(rootElementId = "root") {
        App()
    }
}

@Composable
fun App() {
    var count by remember { mutableStateOf(0) }

    Div(
        attrs = {
            style {
                padding(20.px)
                fontFamily("Arial, sans-serif")
                maxWidth(600.px)
                margin(0.px)
                textAlign("center")
            }
        }
    ) {
        H1(
            attrs = {
                style {
                    color(Color("#333"))
                    marginBottom(30.px)
                }
            }
        ) {
            Text("🐪 Compose HTML 2025")
        }

        Div(
            attrs = {
                style {
                    background("linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
                    padding(30.px)
                    borderRadius(15.px)
                    color(Color.white)
                    marginBottom(20.px)
                }
            }
        ) {
            H2 { Text("Contador: $count") }

            Button(
                attrs = {
                    onClick { count++ }
                    style {
                        padding(15.px, 30.px)
                        backgroundColor(Color("rgba(255,255,255,0.2)"))
                        color(Color.white)
                        border(2.px, LineStyle.Solid, Color.white)
                        borderRadius(25.px)
                        cursor("pointer")
                        fontSize(16.px)
                        fontWeight("bold")
                        marginRight(10.px)
                    }
                }
            ) {
                Text("+ Incrementar")
            }

            Button(
                attrs = {
                    onClick { count-- }
                    style {
                        padding(15.px, 30.px)
                        backgroundColor(Color("rgba(255,255,255,0.2)"))
                        color(Color.white)
                        border(2.px, LineStyle.Solid, Color.white)
                        borderRadius(25.px)
                        cursor("pointer")
                        fontSize(16.px)
                        fontWeight("bold")
                    }
                }
            ) {
                Text("- Decrementar")
            }
        }

        if (count > 10) {
            P(
                attrs = {
                    style {
                        color(Color("#28a745"))
                        fontSize(18.px)
                        fontWeight("bold")
                    }
                }
            ) {
                Text("🎉 Parabéns! Você chegou a $count!")
            }
        }
    }
}