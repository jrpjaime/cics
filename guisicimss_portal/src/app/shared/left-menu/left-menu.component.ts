import { Component, EventEmitter, Output, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';

import { Subscription } from 'rxjs'; // Solo necesitamos Subscription
import { SharedService } from '../services/shared.service';
import { AlertService } from '../services/alert.service';
import { LoaderService } from '../services/loader.service';

// Definimos una interfaz para nuestros elementos de menú para tener un código más limpio
export interface MenuItem {
  name: string;
  icon: string; // Usaremos nombres de clase para los iconos
  route?: string; // Ruta para la navegación
  isExpanded?: boolean; // Para controlar si el submenú está abierto
  children?: MenuItem[]; // Para los subniveles
  action?: 'limpiarContexto'; // Acciones personalizadas (ej. limpiar el estado de algún servicio)
  roles?: string[]; // Para especificar qué roles pueden ver este elemento
  disabled?: boolean; // Para deshabilitar visualmente una opción (aunque el guard la protegerá)
}

@Component({
  selector: 'app-left-menu',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './left-menu.component.html',
  styleUrls: ['./left-menu.component.css']
})
export class LeftMenuComponent implements OnInit, OnDestroy {

  @Output() toggleMenuClicked = new EventEmitter<void>();

  // Se elimina 'estatusSubscription' y 'cveIdEstadoCpa' ya que no se usará la lógica de "baja de contador"

  // Estructura del menú para la administración del Hub CICS
  private fullMenuItems: MenuItem[] = [
    {
      name: 'Seguridad Hub',
      icon: 'bi bi-shield-lock-fill',
      isExpanded: true, // Por defecto, expandimos este menú al cargar
      roles: ['ADMIN'], // Solo visible si el JWT trae el rol ADMIN
      children: [
        {
          name: 'Usuarios',
          icon: 'bi bi-people',
          route: '/admin/usuarios'
        }
      ]
    },
    {
      name: 'Monitorización',
      icon: 'bi bi-speedometer2',
      isExpanded: false, // Por defecto, este menú puede estar colapsado
      roles: ['ADMIN'], // Solo visible si el JWT trae el rol ADMIN
      children: [
        { name: 'Log de Auditoría', icon: 'bi bi-list-columns', route: '/admin/auditoria' },
        { name: 'Dashboard', icon: 'bi bi-graph-up-arrow', route: '/admin/dashboard' }
      ]
    }
  ];

  // Este será el array de menú que se renderizará, filtrado por roles
  menuItems: MenuItem[] = [];
  private rolesSubscription!: Subscription; // Para gestionar la desuscripción de los roles

  constructor(
    private router: Router,
    private sharedService: SharedService, // Inyectamos SharedService
    private alertService: AlertService,
    private loaderService: LoaderService,
    // Se eliminan inyecciones de ContadorPublicoAutorizadoService y SolicitudBajaDataService
  ) { }

  ngOnInit(): void {
    // Nos suscribimos a los cambios de roles del usuario
    this.rolesSubscription = this.sharedService.currentRoleSesion.subscribe(userRoles => {
      console.log('LeftMenuComponent - Roles del usuario recibidos:', userRoles);
      this.filterMenuItems(userRoles);
      // Se elimina la llamada a aplicarRestriccionBaja();
    });

    // Se elimina la suscripción a 'currentCveIdEstadoCpaSesion' y la llamada a aplicarRestriccionBaja();
  }

  ngOnDestroy(): void {
    // Es importante desuscribirse para evitar fugas de memoria
    if (this.rolesSubscription) {
      this.rolesSubscription.unsubscribe();
    }
    // Se elimina la desuscripción de 'estatusSubscription'
  }

  // Se elimina el método 'aplicarRestriccionBaja()' ya que no es relevante para el nuevo contexto.

  /**
   * Filtra los elementos del menú basándose en los roles del usuario logueado.
   * Un elemento es visible si:
   * 1. No tiene roles definidos (es público).
   * 2. O si al menos uno de los roles del usuario coincide con los roles requeridos por el elemento.
   * @param userRoles Array de roles del usuario autenticado.
   */
  private filterMenuItems(userRoles: string[]): void {
    if (!userRoles || userRoles.length === 0) {
      this.menuItems = []; // Si no hay roles, no mostrar nada
      return;
    }

    this.menuItems = this.fullMenuItems.filter(item => {
      // Si el item del menú no tiene roles definidos, es visible por defecto
      if (!item.roles || item.roles.length === 0) {
        return true;
      }
      // Verificar si alguno de los roles del usuario coincide con los roles requeridos para el item del menú
      return item.roles.some(requiredRole => userRoles.includes(requiredRole));
    });
    console.log('LeftMenuComponent - Menú filtrado:', this.menuItems);
  }

  /**
   * Emite un evento para indicar que se debe alternar el estado del menú (contraer/expandir).
   */
  onToggleMenu(): void {
    this.toggleMenuClicked.emit();
  }

  /**
   * Alterna la expansión/contracción de un submenú.
   * Cierra cualquier otro submenú abierto antes de abrir el actual.
   * @param item El MenuItem con submenús a alternar.
   */
  toggleSubmenu(item: MenuItem): void {
    const isOpening = !item.isExpanded;
    // Cierra todos los submenús antes de abrir el seleccionado
    this.menuItems.forEach(i => {
      if (i.children && i !== item) { // Asegura no cerrar el que se va a abrir
        i.isExpanded = false;
      }
    });
    if (isOpening) {
      item.isExpanded = true;
    }
  }

  /**
   * Maneja el clic en un elemento del menú.
   * Previene la navegación si el elemento está deshabilitado o si tiene submenús.
   * @param event El evento del clic.
   * @param item El MenuItem que fue clicado.
   */
  onItemClick(event: MouseEvent, item: MenuItem): void {
    if (item.disabled) {
      event.preventDefault();
      event.stopPropagation(); // Evita la propagación del evento para no afectar otros elementos
      return; // Bloqueo total si está deshabilitado
    }

    // Si tiene una acción personalizada, la ejecuta
    if (item.action) {
      event.preventDefault(); // Previene la navegación por defecto
      if (item.action === 'limpiarContexto') {
        // Ejemplo de acción: limpiar algún contexto específico y redirigir
        this.router.navigate(['/home']);
      }
      return;
    }

    // Si tiene submenús, solo los expande/contrae
    if (item.children) {
      event.preventDefault(); // Previene la navegación por defecto
      this.toggleSubmenu(item);
      return;
    }

    // Si tiene una ruta y no es un submenú ni tiene acción, navega
    if (item.route) {
      event.preventDefault(); // Previene la navegación inmediata
      console.log("Navegando a:", item.route);

      // Se elimina la lógica específica de 'modificaciondatos' y 'solicitudbaja'
      // ya que esos módulos ya no existen.

      // Navega a la ruta, con un pequeño retraso opcional para animaciones si fuera necesario.
      setTimeout(() => {
        this.router.navigate([item.route!]); // Usamos '!' para asegurar que item.route no es undefined
      }, 0);
    }
  }

  // Se elimina el método 'procesarSolicitudBaja()' ya que no es relevante para el nuevo contexto.
}
