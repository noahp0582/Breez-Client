# 1.21.11 hook spike (verified against generated sources)

All names below were read from the Loom-generated Minecraft sources and Fabric API 0.141.6 sources.

- **World render:** `WorldRenderEvents.END_MAIN` (`net.fabricmc.fabric.api.client.rendering.v1.world`), callback `endMain(WorldRenderContext)`. Context gives `matrices()` (`PoseStack`) and `consumers()` (`MultiBufferSource`, really a `BufferSource`). Vertices must be camera-relative. Fires after terrain/entities/translucent, before particles/weather.
- **Camera:** `Minecraft.getInstance().gameRenderer.getMainCamera()`; `position()` -> `Vec3`, `rotation()` -> `Quaternionf`.
- **Through-walls lines/quads:** build own pipelines from `RenderPipelines.LINES_SNIPPET` / `DEBUG_FILLED_SNIPPET` with `.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)`, register via `RenderPipelines.register`, wrap with `RenderType.create(name, RenderSetup.builder(pipeline).createRenderSetup())`. Line vertex format is `POSITION_COLOR_NORMAL_LINE_WIDTH`: `addVertex(...).setColor(argb).setNormal(x,y,z).setLineWidth(w)`.
- **Through-walls text:** `Font.drawInBatch(String, x, y, color, shadow, Matrix4f, MultiBufferSource, Font.DisplayMode.SEE_THROUGH, bgColor, light)`.
- **HUD:** `HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier, (GuiGraphics, DeltaTracker) -> ...)`.
- **Chat intercept:** `ClientSendMessageEvents.ALLOW_CHAT` (`allowSendChatMessage(String) -> boolean`).
- **Screen input (1.21.11 signatures):** `mouseClicked(MouseButtonEvent, boolean)`, `mouseDragged(MouseButtonEvent, double, double)`, `mouseReleased(MouseButtonEvent)`, `mouseScrolled(double,double,double,double)`, `keyPressed(KeyEvent)`, `render(GuiGraphics,int,int,float)`.
- **Key polling:** `InputConstants.isKeyDown(Window, int)`.
- **Chat output:** `Minecraft.gui.getChat().addMessage(Component)`.
