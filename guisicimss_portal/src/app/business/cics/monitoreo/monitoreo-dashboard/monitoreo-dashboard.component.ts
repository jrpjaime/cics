import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms'; // <--- 1. Importa esto
import { MonitoreoService } from '../services/monitoreo.service';
import { LoaderService } from '../../../../shared/services/loader.service';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-monitoreo-dashboard',
  standalone: true,
  // 2. Agrega FormsModule a la lista de imports
  imports: [CommonModule, FormsModule], 
  templateUrl: './monitoreo-dashboard.component.html',
  styleUrl: './monitoreo-dashboard.component.css'
})
export class MonitoreoDashboardComponent implements OnInit {
  private monitoreoService = inject(MonitoreoService);
  private loader = inject(LoaderService);

  filtros = {
    fechaInicio: '',
    fechaFin: ''
  };

  data: any = {
    resumen: {},
    topProgramas: [],
    peoresTiempos: [],
    usuariosActivos: [],
    desgloseErrores: []  
  };

  ngOnInit() {
    this.cargarMetricas();
  }

  cargarMetricas() {
    this.loader.show();
    this.monitoreoService.obtenerMetricas(this.filtros.fechaInicio, this.filtros.fechaFin)
      .pipe(finalize(() => this.loader.hide()))
      .subscribe({
        next: (res) => this.data = res,
        error: (err) => console.error("Error al obtener métricas", err)
      });
  }

  getCalidadServicio(): number {
    if (!this.data.resumen || !this.data.resumen.TOTAL || this.data.resumen.TOTAL === 0) return 0;
    // Usamos Number() para asegurar que la operación aritmética sea correcta si el valor viene como string de la DB
    const exitos = Number(this.data.resumen.EXITOS || 0);
    const total = Number(this.data.resumen.TOTAL || 0);
    return (exitos / total) * 100;
  }

  getPosicionPuntero(): number {
    const latencia = Number(this.data.resumen?.LATENCIA_MEDIA || 0);
    
    // Escala total: 30,000ms (30 segundos)
    // 30,000ms = 100% de la barra
    const porcentaje = (latencia / 30000) * 100;
    
    return Math.min(porcentaje, 100); // Tope al 100% si excede
  }

  // Opcional: Función para el texto de estado dinámico
  getEstadoSLA(): string {
      const lat = Number(this.data.resumen?.LATENCIA_MEDIA || 0);
      if (lat <= 5000) return '🟢 ÓPTIMO';
      if (lat <= 12000) return '🟡 LENTO';
      return '🔴 CRÍTICO';
  }
}