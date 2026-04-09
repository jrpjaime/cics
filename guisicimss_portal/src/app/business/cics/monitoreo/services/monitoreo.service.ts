import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../../../environments/environment'; // Ajusta los ../ según profundidad
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class MonitoreoService {
  private http = inject(HttpClient);
  // Esta URL debe coincidir con el nuevo MonitoreoController del backend
  private readonly API_URL = `${environment.monitoreoApiUrl}/v1/admin/dashboard/metricas`;

obtenerMetricas(fechaInicio?: string, fechaFin?: string): Observable<any> {
  let params = new HttpParams();
  if (fechaInicio) params = params.set('fechaInicio', fechaInicio);
  if (fechaFin) params = params.set('fechaFin', fechaFin);
  return this.http.get(this.API_URL, { params });
}
}
