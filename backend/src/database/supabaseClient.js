const { createClient } = require('@supabase/supabase-js');
const config = require('../config');

let supabaseInstance = null;

function getSupabase() {
  if (supabaseInstance) {
    return supabaseInstance;
  }

  const url = config.supabase?.url || process.env.SUPABASE_URL;
  const key = config.supabase?.secretKey || process.env.SUPABASE_SECRET_KEY || config.supabase?.publishableKey;

  if (!url || !key) {
    return null;
  }

  supabaseInstance = createClient(url, key, {
    auth: {
      persistSession: false,
      autoRefreshToken: false
    }
  });

  return supabaseInstance;
}

module.exports = {
  getSupabase
};
