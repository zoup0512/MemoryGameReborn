@Component
export struct ReadyIndicator {
  @Prop @Watch('draw') progress: number = 0;
  private context: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true));
  private ready: boolean = false;

  private draw(): void {
    if (!this.ready) { return; }
    const c = this.context;
    c.clearRect(0, 0, 120, 120);
    c.beginPath(); c.arc(60, 60, 59, 0, Math.PI * 2);
    c.fillStyle = '#E5F6FE'; c.fill();
    c.beginPath(); c.moveTo(60, 60);
    c.arc(60, 60, 59, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * Math.max(0, Math.min(1, this.progress)));
    c.closePath(); c.fillStyle = '#FFFFFF'; c.fill();
    c.beginPath(); c.arc(60, 60, 59, 0, Math.PI * 2);
    c.strokeStyle = '#D9D9D9'; c.lineWidth = 1; c.stroke();
  }

  build() {
    Canvas(this.context).width(120).height(120).onReady(() => { this.ready = true; this.draw(); })
  }
}
