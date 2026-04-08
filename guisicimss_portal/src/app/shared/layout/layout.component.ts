import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { SidebarComponent } from "../sidebar/sidebar.component";

import { Router, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AlertComponent } from '../app/alert/alert.component';
import { LoaderComponent } from '../loader/loader.component';
import { LeftMenuComponent } from '../left-menu/left-menu.component';
import { ModalService } from '../services/modal.service';
import { timer, map, take } from 'rxjs';
import { Subscription } from 'rxjs';

@Component({
    selector: 'app-layout',
    standalone: true,
    imports: [ SidebarComponent,   RouterOutlet, AlertComponent, LoaderComponent, LeftMenuComponent],
    templateUrl: './layout.component.html',
    styleUrl: './layout.component.css'
})
export  class LayoutComponent  implements OnInit, OnDestroy {

 private cuentaRegresivaSub?: Subscription;

  constructor(
    public router: Router,
	private authService: AuthService,
	private modalService: ModalService
	) { }

  private fechaUltimoMovimiento!: Date;
  private intervalId: any;
  private modalInactividadVisible = false;

  tiempoRestante?:number;
  iniciocuentaregresiva=false;
  tiempoMonitoreo?: number;
  tiempoMonitoreoRestante?: number;
 
	cerrarSesion?: boolean;

  // Tiempo en minutos para considerar inactividad y mostrar el modal
  private readonly MINUTOS_PARA_INACTIVIDAD = 10

  // Segundos que el modal estará visible antes de cerrar sesión
  private readonly SEGUNDOS_DE_ESPERA = 30;

  


public isMenuCollapsed = false;

    toggleMenu(): void {
    this.isMenuCollapsed = !this.isMenuCollapsed;
  }

  


  ngOnInit() {
    this.resetearTemporizadorInactividad();
    this.iniciarMonitoreo();
  }

  ngOnDestroy(): void {
    // Limpia el intervalo cuando el componente se destruye
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  private iniciarMonitoreo(): void {
    // Este único intervalo revisará la inactividad cada segundo
    this.intervalId = setInterval(() => {
      if (!this.authService.isAuthenticated()) {
        return; // Si no está autenticado, no hacemos nada
      }

      const tiempoInactivoMs = new Date().getTime() - this.fechaUltimoMovimiento.getTime();
      const limiteInactividadMs = this.MINUTOS_PARA_INACTIVIDAD * 60 * 1000;

      // Si se supera el tiempo de inactividad Y el modal no está ya visible
      if (tiempoInactivoMs > limiteInactividadMs && !this.modalInactividadVisible) {
        this.mostrarDialogoInactividad();
      }
    }, 1000); // Se ejecuta cada segundo
  }
 
  // --- HOST LISTENERS PARA DETECTAR ACTIVIDAD ---
  @HostListener('document:mousemove')
  @HostListener('document:keypress')
  @HostListener('document:click')
  onUserActivity() {
    // Solo reseteamos si el usuario está logueado y el modal NO está visible
    if (this.authService.isAuthenticated() && !this.modalInactividadVisible) {
      this.resetearTemporizadorInactividad();
    }
  }

  private resetearTemporizadorInactividad(): void {
    this.fechaUltimoMovimiento = new Date();
  }

 



  private mostrarDialogoInactividad(): void {
    this.modalInactividadVisible = true;
    
    // 1. Creamos un observable que emite un valor cada segundo (1000 ms).
    const countdown$ = timer(0, 1000).pipe(
      // 2. Tomamos N+1 valores para ir de N a 0.
      take(this.SEGUNDOS_DE_ESPERA + 1),
      // 3. Transformamos el valor emitido (0, 1, 2...) en una cuenta regresiva (30, 29, 28...).
      map(tick => this.SEGUNDOS_DE_ESPERA - tick)
    );

    // 4. Creamos un observable separado para el texto del botón.
    const buttonText$ = countdown$.pipe(
      map(timeLeft => `Continuar Sesión (${timeLeft})`)
    );

    // 5. Nos suscribimos a la cuenta regresiva para saber cuándo se agota el tiempo.
    this.cuentaRegresivaSub = countdown$.subscribe(timeLeft => {
      if (timeLeft <= 0) {
        // Si el tiempo llega a 0 y el modal sigue visible, cerramos la sesión.
        if (this.modalInactividadVisible) {
          this.modalService.close();
          this.authService.logout();
        }
      }
    });

    this.modalService.showDialog(
      'confirm',
      'warning',
      'Inactividad',
      '¿Desea continuar con su sesión?',
      (confirmado: boolean) => {
        // IMPORTANTE: Limpiamos la suscripción para evitar fugas de memoria.
        this.cuentaRegresivaSub?.unsubscribe();
        this.modalInactividadVisible = false;

        if (confirmado) {
          this.resetearTemporizadorInactividad();
        } else {
          this.authService.logout();
        }
      },
      buttonText$, // <--- ¡AQUÍ PASAMOS EL OBSERVABLE!
      'Cerrar Sesión'
    );
  }	

}
