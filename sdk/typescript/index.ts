/** Manifest helpers run on the developer's computer or server, never inside Auralis. */
export type Capability = 'catalog.read' | 'stream.play' | 'web.embed' | 'skin.apply' | 'party.session';
export interface Skin {
  primary?: string; background?: string; surface?: string; text?: string; muted?: string;
  textScale?: number; rowHeight?: number; cornerRadius?: number; artworkSize?: number;
  layout?: 'standard' | 'compact' | 'covers'; font?: 'sans' | 'serif' | 'mono'; atmosphere?: boolean;
}
export interface ExtensionManifest {
  apiVersion: 1; id: string; name: string; version: string; capabilities: Capability[]; origins: string[];
  provider?: { catalogUrl: string }; web?: { url: string }; skin?: Skin; party?: { url: string };
}
export interface Track { id: string; title: string; artist?: string; streamUrl: string; format?: 'mp3' | 'flac' | 'wav' | 'ogg' }
export interface CatalogResponse { tracks: Track[] }
export type PartyAction = 'create' | 'join' | 'state' | 'enqueue' | 'leave';
export interface PartyRequest { action: PartyAction; session: string; trackId: string }
export interface PartyResponse { session: string; queue: string[]; mode?: 'shared-queue'; left?: boolean }
export function provider(id: string, name: string, baseUrl: string): ExtensionManifest {
  const url = new URL(baseUrl);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) throw new Error('HTTPS base URL required');
  const base = baseUrl.replace(/\/$/, '');
  return { apiVersion: 1, id, name, version: '1.0.0', capabilities: ['catalog.read','stream.play','party.session'], origins: [url.origin], provider: { catalogUrl: `${base}/v1/catalog` }, party: { url: `${base}/v1/party` } };
}
export function serialize(manifest: ExtensionManifest): string { return JSON.stringify(manifest, null, 2) + '\n'; }
