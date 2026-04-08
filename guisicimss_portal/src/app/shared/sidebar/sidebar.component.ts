import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { SharedService } from '../services/shared.service';
import { Observable } from 'rxjs';
import { CommonModule } from '@angular/common';
import { BaseComponent } from '../base/base.component';

@Component({
    selector: 'app-sidebar',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './sidebar.component.html',
    styleUrl: './sidebar.component.css'
})
export class SidebarComponent extends BaseComponent implements OnInit {
  // Propiedades para mostrar en la pantalla
  nombreCompletoDisplay: string = '';

  constructor(
    private authService: AuthService,
    sharedService: SharedService // Mantenemos SharedService para acceder a los datos del usuario
  ) {
    super(sharedService); // Llama al constructor de BaseComponent
  }



  logout(): void {
    this.authService.logout();
  }

  override ngOnInit(): void {
    super.ngOnInit(); // Llama al ngOnInit del BaseComponent para inicializar los Observables

    this.recargaParametros(); // Carga los parámetros del usuario del BaseComponent

    // Suscríbete al Observable del nombre completo
    this.nombreCompleto$.subscribe(nombre => {
      this.nombreCompletoDisplay = nombre;
      console.log('Sidebar - nombreCompletoDisplay actualizado:', this.nombreCompletoDisplay);
    });

    // Si necesitas el RFC y CURP también de forma reactiva en SidebarComponent:
    this.sharedService.currentRfcSesion.subscribe(rfc => {
      this.rfcSesion = rfc; // Las variables de clase del BaseComponent se actualizarán
      console.log('Sidebar - RFC de sesión actualizado:', this.rfcSesion);
    });

    this.sharedService.currentCurpSesion.subscribe(curp => {
      this.curpSesion = curp; // Las variables de clase del BaseComponent se actualizarán
      console.log('Sidebar - CURP de sesión actualizado:', this.curpSesion);
    });

    // O si los necesitas en el template, puedes acceder directamente a las variables del padre
    // o al Observable nombreCompleto$ con el pipe async.
  }




}






 