<?php
/*
 * activate_modules.php — enable Dolibarr modules after installation, the way the admin page
 * (admin/modules.php) does: Dolibarr's own activateModule(), with the full application loaded.
 *
 * Why not the installer's force_install_module? In Dolibarr's step5.php some modules cannot be
 * constructed (e.g. modBom needs CommonObjectLine, which the installer does not load), and the
 * resulting PHP fatal error aborts the whole page. Here each module is activated on its own and any
 * error is caught and reported, so one failure never hides the others (R14 condition 6).
 *
 * Usage (CLI only, as the web server user, inside the Dolibarr container):
 *   php activate_modules.php <htdocs dir> <modA,modB,...> [admin login]
 * Output: one line per module, "OK modX" or "FAIL modX <reason>". Exit 0 if Dolibarr loaded,
 * 1 if it could not be loaded (bad path, no admin user). Prints no secrets.
 */
if (PHP_SAPI !== 'cli') {
	exit(1);
}
define('NOSESSION', '1');
define('NOCSRFCHECK', '1');
define('NOTOKENRENEWAL', '1');

$htdocs = rtrim($argv[1] ?? '', '/');
$modules = array_values(array_filter(array_map('trim', explode(',', $argv[2] ?? ''))));
$login = $argv[3] ?? 'admin';
if ($htdocs === '' || !is_readable($htdocs.'/master.inc.php') || !$modules) {
	fwrite(STDERR, "usage: php activate_modules.php <htdocs dir> <modA,modB,...> [admin login]\n");
	exit(1);
}

chdir($htdocs);
require_once $htdocs.'/master.inc.php';
require_once DOL_DOCUMENT_ROOT.'/core/lib/admin.lib.php';
// Classes that pages normally have loaded before a module descriptor is built.
require_once DOL_DOCUMENT_ROOT.'/core/class/commonobject.class.php';
if (is_readable(DOL_DOCUMENT_ROOT.'/core/class/commonobjectline.class.php')) {
	require_once DOL_DOCUMENT_ROOT.'/core/class/commonobjectline.class.php';
}

global $db, $conf, $langs, $user, $mysoc;
$user = new User($db);
if ($user->fetch(0, $login) <= 0 || empty($user->admin)) {
	fwrite(STDERR, "admin user '$login' not found\n");
	exit(1);
}
if (method_exists($user, 'getrights')) {
	$user->getrights();
} elseif (method_exists($user, 'loadRights')) {
	$user->loadRights();
}

foreach ($modules as $m) {
	try {
		$res = activateModule($m);
		if (!empty($res['errors'])) {
			echo "FAIL $m ".preg_replace('/\s+/', ' ', strip_tags(implode(' | ', $res['errors'])))."\n";
		} else {
			echo "OK $m\n";
		}
	} catch (Throwable $e) {
		echo "FAIL $m ".get_class($e).': '.preg_replace('/\s+/', ' ', $e->getMessage())."\n";
	}
}
exit(0);
