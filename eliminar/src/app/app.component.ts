import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { ModalComponent } from './shared/shared/components/modal/modal.component';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [RouterOutlet, ModalComponent],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'guicics_portal';

  constructor( private authService: AuthService) {

  }

  ngOnInit(): void {
    /*
   if(this.authService.isAuthenticated()) {
    this.authService.autoRefreshToken()
   }*/
   this.authService.iniciarYRestaurarSesion();
  }
}
