import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MonitoreoService } from '../services/monitoreo.service';
import { LoaderService } from '../../../../shared/services/loader.service';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-monitoreo-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './monitoreo-dashboard.component.html',
  styleUrl: './monitoreo-dashboard.component.css'
})
export class MonitoreoDashboardComponent implements OnInit {
  private monitoreoService = inject(MonitoreoService);
  private loader = inject(LoaderService);

  filtros = { fechaInicio: '', fechaFin: '' };

  data: any = {
    resumen: { TOTAL: 0, EXITOS: 0, ERRORES: 0, TIMEOUTS: 0, LATENCIA_MEDIA: 0 },
    topProgramas: [],
    peoresTiempos: [],
    usuariosActivos: [],
    ultimosErrores: []
  };

  ngOnInit() {
    this.inicializarFechas();
    this.cargarMetricas();
  }

  cargarMetricas() {
    this.loader.show();
    this.monitoreoService.obtenerMetricas(this.filtros.fechaInicio, this.filtros.fechaFin)
      .pipe(finalize(() => this.loader.hide()))
      .subscribe({
        next: (res) => this.data = res,
        error: (err) => console.error(err)
      });
  }

  inicializarFechas() {
    const hoy = new Date();
    const anio = hoy.getFullYear();
    const mes = String(hoy.getMonth() + 1).padStart(2, '0');
    const dia = String(hoy.getDate()).padStart(2, '0');
    const fechaHoy = `${anio}-${mes}-${dia}`;

    this.filtros.fechaInicio = `${fechaHoy}T00:00`;
    this.filtros.fechaFin = `${fechaHoy}T23:59`;
  }

  getCalidadServicio(): number {
    const total = Number(this.data.resumen?.TOTAL || 0);
    if (total === 0) return 0;
    return (Number(this.data.resumen.EXITOS || 0) / total) * 100;
  }

  getPosicionPuntero(): number {
    const latencia = Number(this.data.resumen?.LATENCIA_MEDIA || 0);
    // Escala de 60 segundos (60,000ms)
    return Math.min((latencia / 60000) * 100, 100);
  }

  getEstadoSLA(): string {
    const lat = Number(this.data.resumen?.LATENCIA_MEDIA || 0);
    if (lat <= 10000) return '✅ ÓPTIMO';
    if (lat <= 25000) return '⚠️ LENTO';
    if (lat <= 40000) return '🟠 MUY LENTO';
    return '🚨 CRÍTICO';
  }
}
