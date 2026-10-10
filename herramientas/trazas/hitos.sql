-- Los hitos de una apertura en una traza de Macrobenchmark, en ms desde que el sistema empieza a lanzar la Activity
-- (launchingActivity en system_server). Una fila por traza; herramientas/trazas.sh las junta.
with app as (select upid from process where name = 'mx.papeleriapos.android' order by start_ts limit 1),
s as (select s.ts, s.dur, s.name, t.name hilo
      from slice s join thread_track tt on s.track_id = tt.id join thread t using(utid) join app using(upid)),
ini as (select min(ts) t0 from slice where name like 'launchingActivity#%'),
sesion as (select min(ts) t from s where name like 'CameraDevice-%#createCaptureSession')
select
 (select (min(ts) - (select t0 from ini)) / 1e6 from s where name = 'bindApplication') as proceso,
 (select (min(ts) - (select t0 from ini)) / 1e6 from s where name like 'performCreate:%MainActivity') as actividad,
 (select (min(ts) - (select t0 from ini)) / 1e6 from s where name = 'escaner.enlazar') as enlace,
 (select (min(ts) - (select t0 from ini)) / 1e6 from s where name like 'CameraId-%#openCamera') as abrir_camara,
 (select (min(ts + dur) - (select t0 from ini)) / 1e6 from s where name like 'CameraId-%#openCamera') as camara_abierta,
 (select max(dur) / 1e6 from s where hilo = 'RenderThread' and name like 'DrawFrames%' and ts < (select t from sesion)) as cuadro_gpu_max,
 (select (t - (select t0 from ini)) / 1e6 from sesion) as sesion,
 (select (min(ts + dur) - (select t0 from ini)) / 1e6 from s where name like 'CameraDevice-%#createCaptureSession') as sesion_lista,
 (select (min(ts) - (select t0 from ini)) / 1e6 from s where name = 'escaner.cuadro') as primer_analisis,
 (select count(*) from s where hilo = 'RenderThread' and name like 'DrawFrames%'
    and ts > (select t0 from ini) and ts < (select min(ts) from s where name = 'escaner.cuadro')) as cuadros_gpu_antes
from ini;
