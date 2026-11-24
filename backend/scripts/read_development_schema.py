#!/usr/bin/env python3
"""Temporary development-only read-only schema identity check; no credentials logged."""
import os, pathlib, subprocess, tempfile, zipfile

def main():
    if pathlib.Path('/sys/devices/virtual/dmi/id/board_asset_tag').read_text().strip() != 'i-0f2e92d9c7bd8fe06':
        raise SystemExit('Refusing non-development instance')
    available=int(next(line.split()[1] for line in pathlib.Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemAvailable:')))
    if available < 180*1024:
        raise SystemExit('Insufficient memory for bounded read-only JDBC diagnostic')
    pid=subprocess.check_output(['systemctl','show','daynomy-dev','-p','MainPID','--value'],text=True).strip()
    env={**os.environ}
    service_env=dict(item.split(b'=',1) for item in pathlib.Path('/proc/'+pid+'/environ').read_bytes().split(b'\0') if b'=' in item)
    for key in ('DB_URL','DB_USERNAME','DB_PASSWORD'):
        env[key]=service_env[key.encode()].decode()
    with tempfile.TemporaryDirectory(dir=os.environ['RUNNER_TEMP']) as folder:
        root=pathlib.Path(folder);root.chmod(0o700)
        with zipfile.ZipFile('/home/ec2-user/backend/current.jar') as archive:
            name=next(name for name in archive.namelist() if name.startswith('BOOT-INF/lib/postgresql-') and name.endswith('.jar'))
            (root/'jdbc.jar').write_bytes(archive.read(name))
        source=root/'ReadDevelopmentSchema.java'
        source.write_text(SOURCE)
        compiled=subprocess.run(['javac','-J-Xmx32m',str(source)],capture_output=True,timeout=20)
        if compiled.returncode:
            raise SystemExit('JDBC diagnostic compilation failed; no raw output published')
        result=subprocess.run(['java','-Xmx32m','-XX:TieredStopAtLevel=1','-XX:ReservedCodeCacheSize=16m','-cp',str(root)+':'+str(root/'jdbc.jar'),'ReadDevelopmentSchema'],env=env,capture_output=True,text=True,timeout=20)
        for line in result.stdout.splitlines():
            # Program outputs only fixed metadata; never print Java stderr / connection details.
            if line.startswith(('migration=','shared_table=','diagnostic_error=','sql_state=')):
                print(line,flush=True)
        if result.returncode:
            raise SystemExit('Read-only schema diagnostic failed')

SOURCE = 'import java.sql.*;\npublic class ReadDevelopmentSchema {\n  public static void main(String[] args) {\n    try (Connection c = DriverManager.getConnection(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {\n      c.setReadOnly(true);\n      try (Statement s = c.createStatement()) {\n        s.setQueryTimeout(10);\n        try (ResultSet r = s.executeQuery("SELECT version, script, checksum, success FROM flyway_schema_history WHERE version IN (\'97\',\'98\',\'99\') ORDER BY installed_rank")) {\n          while(r.next()) System.out.println("migration="+r.getString(1)+" script="+r.getString(2)+" checksum="+r.getString(3)+" success="+r.getBoolean(4));\n        }\n        try (ResultSet r = s.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema=\'public\' AND table_name IN (\'spring_session\',\'spring_session_attributes\',\'guest_portfolio_analysis_trials\') ORDER BY table_name")) {\n          while(r.next()) System.out.println("shared_table="+r.getString(1));\n        }\n      }\n    } catch (Exception e) {\n      System.out.println("diagnostic_error="+e.getClass().getName());\n      if(e instanceof SQLException) System.out.println("sql_state="+((SQLException)e).getSQLState());\n      System.exit(1);\n    }\n  }\n}\n'
if __name__ == '__main__':
    main()
