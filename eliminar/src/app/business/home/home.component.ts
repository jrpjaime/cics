import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedService } from '../../shared/services/shared.service';
import { BaseComponent } from '../../shared/base/base.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent extends BaseComponent implements OnInit {

  nombreCompleto: string = '';

  constructor(sharedService: SharedService) {
    super(sharedService);
  }

  override ngOnInit(): void {
    console.log('Iniciando Home...');
    this.recargaParametros();

    // Construcción del nombre para mostrar
    // Si los apellidos no vienen en el token, se muestra solo el nombre de usuario
    const nombreRef = `${this.nombreSesion} ${this.primerApellidoSesion} ${this.segundoApellidoSesion}`.trim();
    this.nombreCompleto = nombreRef ? nombreRef : this.nombreSesion;
  }
}
