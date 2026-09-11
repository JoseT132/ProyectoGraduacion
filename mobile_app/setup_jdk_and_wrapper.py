import os
import zipfile
import urllib.request
import tempfile
import shutil
from pathlib import Path

PROJECT_DIR = Path(__file__).parent.resolve()
GRADLE_VERSION = "8.2"
JDK_URL = "https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip"
JDK_ZIP = PROJECT_DIR / "openjdk17.zip"
GRADLE_ZIP = PROJECT_DIR / f"gradle-{GRADLE_VERSION}-bin.zip"
GRADLE_URL = f"https://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip"


def download(url, dst, label):
    if dst.exists():
        print(f"[INFO] {label} ya descargado: {dst}")
        return
    print(f"[INFO] Descargando {label}...")
    urllib.request.urlretrieve(url, dst)
    print(f"[INFO] {label} descargado: {dst}")


def extract(zip_path, dst):
    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(dst)


def find_jdk_home(extract_dir: Path) -> Path:
    # El contenido suele ser algo como jdk-17.0.x+xx
    for d in extract_dir.iterdir():
        if d.is_dir() and (d / "bin" / "javac.exe").exists():
            return d
    raise FileNotFoundError("No se encontró javac.exe dentro del JDK")


def main():
    print("[INFO] Descargando JDK 17 y Gradle 8.2...")
    download(JDK_URL, JDK_ZIP, "JDK 17")
    download(GRADLE_URL, GRADLE_ZIP, "Gradle")

    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        tmp = Path(tmp)
        print("[INFO] Extrayendo JDK 17...")
        jdk_dir = tmp / "jdk"
        extract(JDK_ZIP, jdk_dir)
        jdk_home = find_jdk_home(jdk_dir)

        print("[INFO] Extrayendo Gradle...")
        gradle_dir = tmp / "gradle"
        extract(GRADLE_ZIP, gradle_dir)
        gradle_bin = next(gradle_dir.glob("gradle-*/bin/gradle.bat"))

        os.environ["JAVA_HOME"] = str(jdk_home)
        os.environ["PATH"] = str(jdk_home / "bin") + os.pathsep + os.environ.get("PATH", "")
        print(f"[INFO] JAVA_HOME temporal: {jdk_home}")

        empty_project = tmp / "empty"
        empty_project.mkdir()
        (empty_project / "settings.gradle").write_text('rootProject.name = "wrappergen"\n', encoding="utf-8")

        gradle_user_home = tmp / "gradle_home"
        gradle_user_home.mkdir()
        os.environ["GRADLE_USER_HOME"] = str(gradle_user_home)

        print("[INFO] Generando wrapper...")
        os.chdir(empty_project)
        ret = os.system(f'"{gradle_bin}" --no-daemon wrapper --gradle-version {GRADLE_VERSION}')
        if ret != 0:
            raise RuntimeError("Fallo al generar wrapper")

        (PROJECT_DIR / "gradle" / "wrapper").mkdir(parents=True, exist_ok=True)
        files = [
            (empty_project / "gradlew", PROJECT_DIR / "gradlew"),
            (empty_project / "gradlew.bat", PROJECT_DIR / "gradlew.bat"),
            (empty_project / "gradle" / "wrapper" / "gradle-wrapper.jar", PROJECT_DIR / "gradle" / "wrapper" / "gradle-wrapper.jar"),
            (empty_project / "gradle" / "wrapper" / "gradle-wrapper.properties", PROJECT_DIR / "gradle" / "wrapper" / "gradle-wrapper.properties"),
        ]
        for src, dst in files:
            if src.exists():
                shutil.copy2(src, dst)
                print(f"  -> Copiado {dst.name}")

    print("\n[OK] Wrapper generado.")
    print("IMPORTANTE: Configura Android Studio con JDK 17 para sincronizar el proyecto.")
    print("Puedes usar: File > Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK")


if __name__ == "__main__":
    main()
