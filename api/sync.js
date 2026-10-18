// Vercel Serverless Function: /api/sync
// Guarda os dados do Painel de Vida em um hash do Upstash Redis.
const crypto = require('crypto');

const REDIS_URL = process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL;
const REDIS_TOKEN = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN;
const HASH = 'painel-da-vida';
const KEYS = ['vendas', 'treinoData', 'treinoSessoes', 'treinoBloco', 'treinoCheckins', 'pesoLog',
  'dietaStatus', 'dietaCheck', 'casaTasks', 'casaDone', 'feiraItems', 'livros', 'planoLeitura',
  'wishlistItems', 'agdEventos', 'agdCats', 'facCadeiras', 'facTodoGeral', 'estudoLog', 'notas', 'facAssuntos', 'corCats', 'fundoCats', 'orcamento', 'projCfg', 'dividasCfg'];

async function redis(cmd) {
  const r = await fetch(REDIS_URL, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + REDIS_TOKEN, 'content-type': 'application/json' },
    body: JSON.stringify(cmd)
  });
  const j = await r.json();
  if (!r.ok || j.error) throw new Error(j.error || 'redis ' + r.status);
  return j.result;
}

function safeEqual(a, b) {
  const x = Buffer.from(String(a));
  const y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

module.exports = async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');

  const secret = process.env.SYNC_TOKEN || '';
  if (!secret || !safeEqual(req.headers['x-sync-token'] || '', secret)) {
    return res.status(401).json({ error: 'unauthorized' });
  }
  if (!REDIS_URL || !REDIS_TOKEN) {
    return res.status(500).json({ error: 'redis-nao-configurado' });
  }

  try {
    if (req.method === 'GET') {
      const flat = (await redis(['HGETALL', HASH])) || [];
      const out = {};
      for (let i = 0; i < flat.length; i += 2) {
        try { out[flat[i]] = JSON.parse(flat[i + 1]); } catch (e) { /* ignora valor corrompido */ }
      }
      return res.status(200).json(out);
    }

    if (req.method === 'POST') {
      const body = req.body || {};
      const changes = body.changes || {};
      const force = body.force === true;
      const keys = Object.keys(changes).filter((k) => {
        const c = changes[k];
        return KEYS.includes(k) && c && typeof c.v === 'string' && typeof c.t === 'number';
      });
      if (!keys.length) return res.status(200).json({ saved: [] });

      let toSave = keys;
      if (!force) {
        const current = await redis(['HMGET', HASH, ...keys]);
        toSave = keys.filter((k, i) => {
          if (!current[i]) return true;
          try { return changes[k].t > JSON.parse(current[i]).t; } catch (e) { return true; }
        });
      }
      if (toSave.length) {
        const args = ['HSET', HASH];
        toSave.forEach((k) => args.push(k, JSON.stringify({ v: changes[k].v, t: changes[k].t })));
        await redis(args);
      }
      return res.status(200).json({ saved: toSave });
    }

    return res.status(405).json({ error: 'metodo-nao-permitido' });
  } catch (e) {
    return res.status(500).json({ error: String((e && e.message) || e) });
  }
};
