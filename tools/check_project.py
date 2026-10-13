"""Offline structural checks, not a substitute for Gradle/Android runtime tests."""
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
ANDROID = '{http://schemas.android.com/apk/res/android}'
xml_files = [ROOT / 'app/src/main/AndroidManifest.xml'] + sorted(RES.glob('**/*.xml'))
for file in xml_files:
    ET.parse(file)
    print('XML OK:', file.relative_to(ROOT))

# View IDs in source must be backed by a resource; repeat ID attributes allowed per layout.
view_ids = set()
for file in RES.glob('layout/*.xml'):
    root = ET.parse(file).getroot()
    for elem in root.iter():
        idval = elem.get(ANDROID + 'id', '')
        if idval.startswith('@+id/'):
            view_ids.add(idval[len('@+id/'):])
sources = list((ROOT / 'app/src/main/java').rglob('*.java'))
for src in sources:
    for name in re.findall(r'R\.id\.(\w+)', src.read_text()):
        assert name in view_ids, f'Missing @+id/{name} referenced by {src}'

manifest = ET.parse(xml_files[0]).getroot()
activities = [e.get(ANDROID + 'name') for e in manifest.findall('.//activity')]
assert '.MainActivity' in activities and '.LoginBrowserActivity' in activities
for drawable in ('glass_panel.xml', 'glass_button.xml'):
    assert (RES / 'drawable' / drawable).is_file()

main = (ROOT / 'app/src/main/java/com/example/gallerygrabber/MainActivity.java').read_text()
login = (ROOT / 'app/src/main/java/com/example/gallerygrabber/LoginBrowserActivity.java').read_text()
wallpaper = (ROOT / 'app/src/main/java/com/example/gallerygrabber/WallpaperStore.java').read_text()
assert 'WallpaperStore.accept' in main and 'WallpaperStore.apply' in main
assert 'catch (InterruptedException interrupted)' in main and 'Thread.currentThread().interrupt()' in main
assert 'refreshSiteCookies' in main and 'LoginBrowserActivity.class' in main
assert 'browser_home' in login and 'browser_back' in login and 'browser_forward' in login
assert 'getCookie(siteHome)' in login and 'not verified' in login
assert 'ACTION_OPEN_DOCUMENT' in wallpaper and 'takePersistableUriPermission' in wallpaper
assert 'setTargetSize' in wallpaper

# Independently compile and run URL-domain rules using a normal JVM.
with tempfile.TemporaryDirectory() as temp:
    tmp = Path(temp)
    rules = ROOT / 'app/src/main/java/com/example/gallerygrabber/SiteRules.java'
    harness = tmp / 'CheckSiteRules.java'
    harness.write_text('''import com.example.gallerygrabber.SiteRules;
public class CheckSiteRules {
 public static void main(String[] args) {
  if (!"V2PH".equals(SiteRules.site("https://www.v2ph.com/album/z7xn5x4a.html?page=2"))) throw new AssertionError();
  if (!"Xasiat".equals(SiteRules.site("https://www.xasiat.com/albums/28156/title/"))) throw new AssertionError();
  if (SiteRules.site("https://www.v2ph.com.evil.test/album/z7xn5x4a.html") != null) throw new AssertionError();
  if (SiteRules.site("http://www.xasiat.com/albums/28156/title/") != null) throw new AssertionError();
  System.out.println("SiteRules JVM tests OK");
 }
}''')
    subprocess.run(['javac', '-d', str(tmp), str(rules), str(harness)], check=True)
    subprocess.run(['java', '-cp', str(tmp), 'CheckSiteRules'], check=True)
print('Project structural checks passed. Full Android build still required.')
