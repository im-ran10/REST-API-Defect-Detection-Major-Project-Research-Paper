<?php
/*
 * list_api_modules.php — R14 generic, defect-independent rule for Dolibarr's module configuration.
 *
 * Prints the comma-separated list of module descriptor classes (modXxx) that provide REST API
 * classes, using Dolibarr's OWN discovery logic from htdocs/api/index.php (the code that builds
 * /api/index.php/explorer/swagger.json):
 *   - scan the module directories returned by the same rule as dolGetModulesDirs()
 *     (htdocs/core/modules/ and htdocs/<dir>/core/modules/, skipping names containing "disabled");
 *   - for each descriptor mod<X>.class.php, map <x> to its API directory with Dolibarr's own
 *     getModuleDirForApiClass();
 *   - the module is API-capable if htdocs/<that dir>/class/ contains an api_*.class.php file other
 *     than api_access.class.php (which index.php also skips).
 * Nothing here looks at defects, issues or benchmark files.
 *
 * Usage (inside the Dolibarr web container, or any PHP CLI with the Dolibarr source):
 *   php list_api_modules.php /var/www/html            -> modA,modB,...      (stdout)
 *   php list_api_modules.php /var/www/html --details  -> one line per module: class, api dir, api files
 * Exit code 1 if the source tree or Dolibarr's mapping function cannot be found.
 */

$htdocs = rtrim($argv[1] ?? '', '/');
$details = in_array('--details', $argv, true);
$lib = $htdocs.'/core/lib/functions2.lib.php';
if ($htdocs === '' || !is_readable($lib)) {
	fwrite(STDERR, "usage: php list_api_modules.php <htdocs dir> [--details]\n");
	exit(1);
}

// Reuse Dolibarr's own mapping function without bootstrapping Dolibarr (it is a pure function).
// Extract it from functions2.lib.php so that no other library code runs.
$src = file_get_contents($lib);
if (!preg_match('/function getModuleDirForApiClass\(\$moduleobject\)\s*\{.*?\n\}\n/s', $src, $m)) {
	fwrite(STDERR, "getModuleDirForApiClass() not found in $lib\n");
	exit(1);
}
eval($m[0]);

// Module directories, same rule as dolGetModulesDirs() for the 'main' document root.
$moduledirs = array($htdocs.'/core/modules/');
foreach (scandir($htdocs) as $f) {
	if (preg_match('/disabled/', $f) || $f[0] === '.' || substr($f, 0, 3) === 'CVS' || $f === 'includes') {
		continue;
	}
	if (is_dir("$htdocs/$f") && is_dir("$htdocs/$f/core/modules/")) {
		$moduledirs[] = "$htdocs/$f/core/modules/";
	}
}

$found = array();
foreach ($moduledirs as $dir) {
	foreach (scandir($dir) as $file) {
		if (!preg_match('/^mod(.*)\.class\.php$/i', $file, $reg)) {
			continue;
		}
		$module = strtolower($reg[1]);
		$apidir = getModuleDirForApiClass($module);
		$classdir = $htdocs.'/'.$apidir.'/class/';
		if (!is_dir($classdir)) {
			continue;
		}
		$apifiles = array();
		foreach (scandir($classdir) as $cf) {
			if ($cf !== 'api_access.class.php' && preg_match('/^api_(.*)\.class\.php$/i', $cf)) {
				$apifiles[] = $cf;
			}
		}
		if ($apifiles) {
			$class = 'mod'.$reg[1];
			// step5.php can only activate descriptors under core/modules/ (dol_include_once("/core/modules/...")).
			$where = ($dir === $htdocs.'/core/modules/') ? 'core' : substr($dir, strlen($htdocs) + 1);
			$found[$class] = array($apidir, $apifiles, $where);
		}
	}
}
ksort($found, SORT_STRING | SORT_FLAG_CASE);

if ($details) {
	foreach ($found as $class => list($apidir, $apifiles, $where)) {
		printf("%-24s %-18s %-14s %s\n", $class, $apidir, $where, implode(' ', $apifiles));
	}
} else {
	echo implode(',', array_keys($found)), "\n";
}
