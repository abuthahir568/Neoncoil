/**
 * Lightweight 2D Spatial Hash Grid for authoritative collision checks and viewport interest culling.
 */
export class SpatialGrid<T> {
  private readonly invCellSize: number;
  private readonly buckets: Map<number, T[]> = new Map();

  constructor(cellSize: number) {
    this.invCellSize = 1 / Math.max(16, cellSize);
  }

  clear(): void {
    this.buckets.clear();
  }

  private key(cx: number, cy: number): number {
    // Pack signed 16-bit cell indices into a safe 32-bit integer
    return ((cx & 0xffff) << 16) | (cy & 0xffff);
  }

  insert(x: number, y: number, item: T): void {
    const cx = Math.floor(x * this.invCellSize);
    const cy = Math.floor(y * this.invCellSize);
    const k = this.key(cx, cy);
    let bucket = this.buckets.get(k);
    if (!bucket) {
      bucket = [];
      this.buckets.set(k, bucket);
    }
    bucket.push(item);
  }

  queryBox(minX: number, minY: number, maxX: number, maxY: number, visit: (item: T) => void): void {
    const minCx = Math.floor(minX * this.invCellSize);
    const maxCx = Math.floor(maxX * this.invCellSize);
    const minCy = Math.floor(minY * this.invCellSize);
    const maxCy = Math.floor(maxY * this.invCellSize);

    for (let cy = minCy; cy <= maxCy; cy++) {
      for (let cx = minCx; cx <= maxCx; cx++) {
        const bucket = this.buckets.get(this.key(cx, cy));
        if (!bucket) continue;
        for (let i = 0; i < bucket.length; i++) {
          visit(bucket[i]);
        }
      }
    }
  }

  queryRadius(x: number, y: number, radius: number, visit: (item: T) => void): void {
    this.queryBox(x - radius, y - radius, x + radius, y + radius, visit);
  }
}
