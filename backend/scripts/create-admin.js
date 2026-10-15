const { getDb } = require('../src/database/db');
const { createAdminAccount } = require('../src/services/adminService');

async function main() {
  const args = process.argv.slice(2);
  if (args.length < 2) {
    console.log('Usage: node scripts/create-admin.js <ADMIN_ID> <PASSWORD> [DISPLAY_NAME]');
    console.log('Example: node scripts/create-admin.js ADM-MASTER-02 MySecretPass123! "Administrator 2"');
    process.exit(1);
  }

  const [id, password, displayName] = args;
  try {
    const result = await createAdminAccount({
      id,
      password,
      displayName: displayName || 'System Administrator',
      actorId: 'cli'
    });
    console.log('Admin account created successfully:');
    console.log(JSON.stringify(result.admin, null, 2));
    process.exit(0);
  } catch (err) {
    console.error('Failed to create admin:', err.message);
    process.exit(1);
  }
}

main();
